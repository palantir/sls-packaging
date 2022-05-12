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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.immutables.value.Value;

public abstract class LaunchConfigTask extends DefaultTask {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final ImmutableList<String> java8gcLoggingOptions = ImmutableList.of(
            "-XX:+PrintGCDateStamps",
            "-XX:+PrintGCDetails",
            "-XX:-TraceClassUnloading",
            "-XX:+UseGCLogFileRotation",
            "-XX:GCLogFileSize=10M",
            "-XX:NumberOfGCLogFiles=10",
            "-Xloggc:var/log/gc-%t-%p.log",
            "-verbose:gc");
    private static final ImmutableList<String> java14PlusOptions =
            ImmutableList.of("-XX:+ShowCodeDetailsInExceptionMessages");
    private static final ImmutableList<String> java15Options =
            ImmutableList.of("-XX:+UnlockDiagnosticVMOptions", "-XX:+ExpandSubTypeCheckAtParseTime");
    private static final ImmutableList<String> disableBiasedLocking = ImmutableList.of("-XX:-UseBiasedLocking");

    private static final ImmutableList<String> alwaysOnJvmOptions = ImmutableList.of(
            "-XX:+CrashOnOutOfMemoryError",
            "-Djava.io.tmpdir=var/data/tmp",
            "-XX:ErrorFile=var/log/hs_err_pid%p.log",
            "-XX:HeapDumpPath=var/log",
            // Set DNS cache TTL to 20s to account for systems such as RDS and other
            // AWS-managed systems that modify DNS records on failover.
            "-Dsun.net.inetaddr.ttl=20",
            "-XX:NativeMemoryTracking=summary",
            // Increase default JFR stack depth beyond the default (conservative) 64 frames.
            // This can be overridden by user-provided options.
            // See sls-packaging#1230
            "-XX:FlightRecorderOptions=stackdepth=256");

    // Reduce memory usage for some versions of glibc.
    // Default value is 8 * CORES.
    // See https://issues.apache.org/jira/browse/HADOOP-7154
    public static final Map<String, String> defaultEnvironment = ImmutableMap.of("MALLOC_ARENA_MAX", "4");

    private final Property<String> mainClass = getProject().getObjects().property(String.class);
    private final Property<String> serviceName = getProject().getObjects().property(String.class);
    private final ListProperty<String> gcJvmOptions = getProject().getObjects().listProperty(String.class);
    private final Property<Boolean> addJava8GcLogging =
            getProject().getObjects().property(Boolean.class);
    private final Property<String> javaHome = getProject().getObjects().property(String.class);
    private final Property<JavaVersion> javaVersion = getProject().getObjects().property(JavaVersion.class);
    private final ListProperty<String> args = getProject().getObjects().listProperty(String.class);
    private final ListProperty<String> checkArgs = getProject().getObjects().listProperty(String.class);
    private final ListProperty<String> defaultJvmOpts =
            getProject().getObjects().listProperty(String.class);

    private final MapProperty<String, String> env = getProject().getObjects().mapProperty(String.class, String.class);
    private RegularFileProperty staticLauncher = getProject().getObjects().fileProperty();
    private RegularFileProperty checkLauncher = getProject().getObjects().fileProperty();

    @SuppressWarnings("PublicConstructorForAbstractClass")
    public LaunchConfigTask() {
        staticLauncher.set(getProject().getLayout().getBuildDirectory().file("scripts/launcher-static.yml"));
        checkLauncher.set(getProject().getLayout().getBuildDirectory().file("scripts/launcher-check.yml"));
    }

    @Input
    public final Property<String> getMainClass() {
        return mainClass;
    }

    @Input
    public final Property<String> getServiceName() {
        return serviceName;
    }

    @Input
    public final ListProperty<String> getGcJvmOptions() {
        return gcJvmOptions;
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
    public final Property<JavaVersion> getJavaVersion() {
        return javaVersion;
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
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * The difference between fullClasspath and classpath is that classpath is what is written
     * to the launcher-static.yml file, this <i>might</i> in some cases be the manifest classpath
     * JAR. Full Classpath on the other hand is always going to be the full set of JARs which may
     * be the same as classpath if manifest classpath JARs are not used.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getFullClasspath();

    @InputFiles
    public abstract ConfigurableFileCollection getJavaAgents();

    @OutputFile
    public final RegularFileProperty getStaticLauncher() {
        return staticLauncher;
    }

    @OutputFile
    public final RegularFileProperty getCheckLauncher() {
        return checkLauncher;
    }

    @TaskAction
    public final void createConfig() throws IOException {
        writeConfig(
                LaunchConfig.builder()
                        .mainClass(mainClass.get())
                        .serviceName(serviceName.get())
                        .javaHome(javaHome.getOrElse(""))
                        .args(args.get())
                        .classpath(relativizeToServiceLibDirectory(getClasspath()))
                        .addAllJvmOpts(javaAgentArgs())
                        .addAllJvmOpts(alwaysOnJvmOptions)
                        .addAllJvmOpts(addJava8GcLogging.get() ? java8gcLoggingOptions : ImmutableList.of())
                        .addAllJvmOpts(
                                javaVersion.get().compareTo(JavaVersion.toVersion("14")) >= 0
                                        ? java14PlusOptions
                                        : ImmutableList.of())
                        .addAllJvmOpts(
                                javaVersion.get().compareTo(JavaVersion.toVersion("15")) == 0
                                        ? java15Options
                                        : ImmutableList.of())
                        // Biased locking is disabled on java 15+ https://openjdk.java.net/jeps/374
                        // We disable biased locking on all releases in order to reduce safepoint time,
                        // revoking biased locks requires a safepoint, and can occur for non-obvious
                        // reasons, e.g. System.identityHashCode.
                        .addAllJvmOpts(
                                javaVersion.get().compareTo(JavaVersion.toVersion("15")) < 0
                                        ? disableBiasedLocking
                                        : ImmutableList.of())
                        .addAllJvmOpts(
                                ModuleArgs.collectClasspathArgs(getProject(), javaVersion.get(), getFullClasspath()))
                        .addAllJvmOpts(gcJvmOptions.get())
                        .addAllJvmOpts(defaultJvmOpts.get())
                        .putAllEnv(defaultEnvironment)
                        .putAllEnv(env.get())
                        .build(),
                getStaticLauncher().get().getAsFile());

        writeConfig(
                LaunchConfig.builder()
                        .mainClass(mainClass.get())
                        .serviceName(serviceName.get())
                        .javaHome(javaHome.getOrElse(""))
                        .args(checkArgs.get())
                        .classpath(relativizeToServiceLibDirectory(getClasspath()))
                        .addAllJvmOpts(javaAgentArgs())
                        .addAllJvmOpts(alwaysOnJvmOptions)
                        .addAllJvmOpts(defaultJvmOpts.get())
                        .env(defaultEnvironment)
                        .build(),
                getCheckLauncher().get().getAsFile());
    }

    private static void writeConfig(LaunchConfig config, File scriptFile) throws IOException {
        Files.createDirectories(scriptFile.getParentFile().toPath());
        OBJECT_MAPPER.writeValue(scriptFile, config);
    }

    private List<String> javaAgentArgs() {
        return getJavaAgents().getFiles().stream()
                .map(file -> "-javaagent:service/lib/agent/"
                        + validateJavaAgent(file).getName())
                .collect(Collectors.toList());
    }

    /** Returns the input file. An exception is thrown if the {@code agentFile} is not a java agent. */
    private static File validateJavaAgent(File agentFile) {
        try {
            JarFile agentJarFile = new JarFile(agentFile);
            if (!agentJarFile.getManifest().getMainAttributes().containsKey(new Attributes.Name("Premain-Class"))) {
                throw new IllegalArgumentException("Jar file " + agentFile.getName()
                        + " is not a java agent and contains no Premain-Class manifest entry");
            }
            return agentFile;
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static List<String> relativizeToServiceLibDirectory(FileCollection files) {
        return files.getFiles().stream()
                .map(file -> "service/lib/" + file.getName())
                .collect(Collectors.toList());
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableLaunchConfig.class)
    @JsonDeserialize(as = ImmutableLaunchConfig.class)
    public interface LaunchConfig {
        // keep in sync with StaticLaunchConfig struct in go-java-launcher
        @Value.Default
        default String configType() {
            return "java";
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
