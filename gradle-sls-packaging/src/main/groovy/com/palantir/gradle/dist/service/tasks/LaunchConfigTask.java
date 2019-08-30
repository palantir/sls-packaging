/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.dist.service.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.dist.service.gc.GcProfile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.immutables.value.Value;

public class LaunchConfigTask extends DefaultTask {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final List<String> java8gcLoggingOptions = ImmutableList.of(
            "-XX:+PrintGCDateStamps",
            "-XX:+PrintGCDetails",
            "-XX:-TraceClassUnloading",
            "-XX:+UseGCLogFileRotation",
            "-XX:GCLogFileSize=10M",
            "-XX:NumberOfGCLogFiles=10",
            "-Xloggc:var/log/gc-%t-%p.log",
            "-verbose:gc"
    );

    private static final List<String> alwaysOnJvmOptions = ImmutableList.of(
            "-XX:+CrashOnOutOfMemoryError",
            "-Djava.io.tmpdir=var/data/tmp",
            "-XX:ErrorFile=var/log/hs_err_pid%p.log",
            "-XX:HeapDumpPath=var/log",
            // Set DNS cache TTL to 20s to account for systems such as RDS and other
            // AWS-managed systems that modify DNS records on failover.
            "-Dsun.net.inetaddr.ttl=20"
    );

    // Reduce memory usage for some versions of glibc.
    // Default value is 8 * CORES.
    // See https://issues.apache.org/jira/browse/HADOOP-7154
    public static final Map<String, String> defaultEnvironment = ImmutableMap.of("MALLOC_ARENA_MAX", "4");

    private final Property<String> mainClass = getProject().getObjects().property(String.class);
    private final Property<String> serviceName = getProject().getObjects().property(String.class);
    private final Property<GcProfile> gc = getProject().getObjects().property(GcProfile.class);
    private final Property<Boolean> addJava8GcLogging = getProject().getObjects().property(Boolean.class);
    private final Property<String> javaHome = getProject().getObjects().property(String.class);
    private final ListProperty<String> args = getProject().getObjects().listProperty(String.class);
    private final ListProperty<String> checkArgs = getProject().getObjects().listProperty(String.class);
    private final ListProperty<String> defaultJvmOpts = getProject().getObjects().listProperty(String.class);

    private final MapProperty<String, String> env = getProject().getObjects().mapProperty(String.class, String.class);

    // TODO(forozco): Use RegularFileProperty once our minimum supported version is 5.0
    private File staticLauncher = new File(getProject().getBuildDir(), "scripts/launcher-static.yml");
    private File checkLauncher = new File(getProject().getBuildDir(), "scripts/launcher-check.yml");

    private FileCollection classpath;

    public LaunchConfigTask() { }

    @Input
    public final Property<String> getMainClass() {
        return mainClass;
    }

    @Input
    public final Property<String> getServiceName() {
        return serviceName;
    }

    public final Property<GcProfile> getGc() {
        return gc;
    }

    // HACKHACK Property<GcProfile> failed to serialise
    @Input
    public final GcProfile gc() {
        return gc.get();
    }

    @Input
    public final Property<Boolean> getAddJava8GcLogging() {
        return addJava8GcLogging;
    }

    @Input
    @Optional
    public final Property<String> getJavaHome() {
        return javaHome;
    }

    @Input
    public final ListProperty<String> getArgs() {
        return args;
    }

    @Input
    public final ListProperty<String> getCheckArgs() {
        return checkArgs;
    }

    @Input
    public final ListProperty<String> getDefaultJvmOpts() {
        return defaultJvmOpts;
    }

    @Input
    public final MapProperty<String, String> getEnv() {
        return env;
    }

    @InputFiles
    public final FileCollection getClasspath() {
        return classpath;
    }

    public final void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @OutputFile
    public final File getStaticLauncher() {
        return staticLauncher;
    }

    public final void setStaticLauncher(File staticLauncher) {
        this.staticLauncher = staticLauncher;
    }

    @OutputFile
    public final File getCheckLauncher() {
        return checkLauncher;
    }

    public final void setCheckLauncher(File checkLauncher) {
        this.checkLauncher = checkLauncher;
    }

    @TaskAction
    public final void createConfig() throws IOException {
        writeConfig(LaunchConfig.builder()
                .mainClass(mainClass.get())
                .serviceName(serviceName.get())
                .javaHome(javaHome.getOrElse(""))
                .args(args.get())
                .classpath(relativizeToServiceLibDirectory(classpath))
                .addAllJvmOpts(alwaysOnJvmOptions)
                .addAllJvmOpts(addJava8GcLogging.get() ? java8gcLoggingOptions : ImmutableList.of())
                .addAllJvmOpts(gc.get().gcJvmOpts())
                .addAllJvmOpts(defaultJvmOpts.get())
                .putAllEnv(defaultEnvironment)
                .putAllEnv(env.get())
                .build(), getStaticLauncher());

        writeConfig(LaunchConfig.builder()
                .mainClass(mainClass.get())
                .serviceName(serviceName.get())
                .javaHome(javaHome.getOrElse(""))
                .args(checkArgs.get())
                .classpath(relativizeToServiceLibDirectory(classpath))
                .addAllJvmOpts(alwaysOnJvmOptions)
                .addAllJvmOpts(defaultJvmOpts.get())
                .env(defaultEnvironment)
                .build(), getCheckLauncher());
    }

    private static void writeConfig(LaunchConfig config, File scriptFile) throws IOException {
        Files.createDirectories(scriptFile.getParentFile().toPath());
        OBJECT_MAPPER.writeValue(scriptFile, config);
    }

    private static List<String> relativizeToServiceLibDirectory(FileCollection files) {
        return files.getFiles().stream()
                .map(file -> String.format("service/lib/%s", file.getName()))
                .collect(Collectors.toList());
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableLaunchConfig.class)
    @JsonDeserialize(as = ImmutableLaunchConfig.class)
    public interface LaunchConfig {
        // keep in sync with StaticLaunchConfig struct in go-java-launcher
        @Value.Default
        default String configType() {
            return  "java";
        }

        @Value.Default
        default int configVersion() {
            return 1;
        }

        @Value.Default
        default List<String> dirs() {
            return ImmutableList.of("var/data/tmp");
        }

        String mainClass();
        String serviceName();
        String javaHome();
        List<String> classpath();
        List<String> jvmOpts();
        List<String> args();
        Map<String, String> env();

        static Builder builder() {
            return new Builder();
        }

        final class Builder extends ImmutableLaunchConfig.Builder {}
    }
}
