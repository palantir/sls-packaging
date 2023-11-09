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
import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
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
import org.immutables.value.Value;

@AutoParallelizable
public final class LaunchConfig {
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
    // Disable C2 compilation for problematic structure in JDK 11.0.16, see https://bugs.openjdk.org/browse/JDK-8291665
    private static final ImmutableList<String> jdk11DisableC2Compile =
            ImmutableList.of("-XX:CompileCommand=exclude,sun/security/ssl/SSLEngineInputRecord.decodeInputRecord");

    private static final ImmutableList<String> alwaysOnJvmOptions = ImmutableList.of(
            "-XX:+CrashOnOutOfMemoryError",
            "-Djava.io.tmpdir=var/data/tmp",
            "-XX:ErrorFile=var/log/hs_err_pid%p.log",
            "-XX:HeapDumpPath=var/log",
            // Set DNS cache TTL to 20s to account for systems such as RDS and other
            // AWS-managed systems that modify DNS records on failover.
            "-Dsun.net.inetaddr.ttl=20",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-XX:NativeMemoryTracking=summary",
            // Increase default JFR stack depth beyond the default (conservative) 64 frames.
            // This can be overridden by user-provided options.
            // See sls-packaging#1230
            "-XX:FlightRecorderOptions=stackdepth=256");

    // Disable AVX-512 intrinsics due to AES/CTR corruption bug in https://bugs.openjdk.org/browse/JDK-8292158
    // UseAVX is not recognized on some platforms (arm), so we must include 'IgnoreUnrecognizedVMOptions' above.
    // When a system supports UseAVX=N, setting UseAVX=N+1 will set the flag to the highest supported value.
    private static final ImmutableList<String> disableAvx512 = ImmutableList.of("-XX:UseAVX=2");

    // Reduce memory usage for some versions of glibc.
    // Default value is 8 * CORES.
    // See https://issues.apache.org/jira/browse/HADOOP-7154
    public static final Map<String, String> defaultEnvironment = ImmutableMap.of("MALLOC_ARENA_MAX", "4");

    interface Params {
        @Input
        Property<String> getMainClass();

        @Input
        Property<String> getServiceName();

        @Input
        ListProperty<String> getGcJvmOptions();

        @Input
        Property<Boolean> getAddJava8GcLogging();

        @Input
        @Optional
        Property<String> getJavaHome();

        @Input
        Property<JavaVersion> getJavaVersion();

        @Input
        Property<Boolean> getBundledJdks();

        @Input
        ListProperty<String> getArgs();

        @Input
        ListProperty<String> getCheckArgs();

        @Input
        ListProperty<String> getDefaultJvmOpts();

        @Input
        MapProperty<String, String> getEnv();

        @InputFiles
        ConfigurableFileCollection getClasspath();

        /**
         * The difference between fullClasspath and classpath is that classpath is what is written
         * to the launcher-static.yml file, this <i>might</i> in some cases be the manifest classpath
         * JAR. Full Classpath on the other hand is always going to be the full set of JARs which may
         * be the same as classpath if manifest classpath JARs are not used.
         */
        @InputFiles
        ConfigurableFileCollection getFullClasspath();

        @InputFiles
        ConfigurableFileCollection getJavaAgents();

        @OutputFile
        RegularFileProperty getStaticLauncher();

        @OutputFile
        RegularFileProperty getCheckLauncher();
    }

    static void action(Params params) {
        JavaVersion javaVersion = params.getJavaVersion().get();
        List<String> avxOptions = getAvxOptions(params);

        writeConfig(
                LaunchConfigInfo.builder()
                        .mainClass(params.getMainClass().get())
                        .serviceName(params.getServiceName().get())
                        .javaHome(params.getJavaHome().getOrElse(""))
                        .args(params.getArgs().get())
                        .classpath(relativizeToServiceLibDirectory(params.getClasspath()))
                        .addAllJvmOpts(javaAgentArgs(params))
                        .addAllJvmOpts(alwaysOnJvmOptions)
                        .addAllJvmOpts(avxOptions)
                        .addAllJvmOpts(params.getAddJava8GcLogging().get() ? java8gcLoggingOptions : ImmutableList.of())
                        // Java 11.0.16 introduced a potential memory leak issues when using the C2
                        // compiler, resolved in 11.0.16.1
                        .addAllJvmOpts(
                                javaVersion.compareTo(JavaVersion.toVersion("11")) == 0
                                                && !params.getBundledJdks().get()
                                        ? jdk11DisableC2Compile
                                        : ImmutableList.of())
                        .addAllJvmOpts(
                                javaVersion.compareTo(JavaVersion.toVersion("14")) >= 0
                                        ? java14PlusOptions
                                        : ImmutableList.of())
                        .addAllJvmOpts(
                                javaVersion.compareTo(JavaVersion.toVersion("15")) == 0
                                        ? java15Options
                                        : ImmutableList.of())
                        // Biased locking is disabled on java 15+ https://openjdk.java.net/jeps/374
                        // We disable biased locking on all releases in order to reduce safepoint time,
                        // revoking biased locks requires a safepoint, and can occur for non-obvious
                        // reasons, e.g. System.identityHashCode.
                        .addAllJvmOpts(
                                javaVersion.compareTo(JavaVersion.toVersion("15")) < 0
                                        ? disableBiasedLocking
                                        : ImmutableList.of())
                        .addAllJvmOpts(ModuleArgs.collectClasspathArgs(javaVersion, params.getFullClasspath()))
                        .addAllJvmOpts(params.getGcJvmOptions().get())
                        .addAllJvmOpts(params.getDefaultJvmOpts().get())
                        .putAllEnv(defaultEnvironment)
                        .putAllEnv(params.getEnv().get())
                        .build(),
                params.getStaticLauncher().get().getAsFile());

        writeConfig(
                LaunchConfigInfo.builder()
                        .mainClass(params.getMainClass().get())
                        .serviceName(params.getServiceName().get())
                        .javaHome(params.getJavaHome().getOrElse(""))
                        .args(params.getCheckArgs().get())
                        .classpath(relativizeToServiceLibDirectory(params.getClasspath()))
                        .addAllJvmOpts(javaAgentArgs(params))
                        .addAllJvmOpts(alwaysOnJvmOptions)
                        .addAllJvmOpts(avxOptions)
                        .addAllJvmOpts(params.getDefaultJvmOpts().get())
                        .env(defaultEnvironment)
                        .build(),
                params.getCheckLauncher().get().getAsFile());
    }

    // When a specific jdk is provided, we can assume a modern versions including the
    // bugfix for JDK-8292158. Only Java versions 11-19 were impacted by this bug, so
    // we don't need to worry about newer releases.
    // Update for Java 20-21:
    // https://bugs.openjdk.org/browse/JDK-8317121
    // https://mail.openjdk.org/pipermail/hotspot-compiler-dev/2023-September/068447.html
    // AVX-512 is largely unreliable, so we're going to opt out entirely for the time
    // being.
    private static List<String> getAvxOptions(Params _params) {
        return disableAvx512;
    }

    private static void writeConfig(LaunchConfigInfo config, File scriptFile) {
        try {
            Files.createDirectories(scriptFile.getParentFile().toPath());
            OBJECT_MAPPER.writeValue(scriptFile, config);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write config", e);
        }
    }

    private static List<String> javaAgentArgs(Params params) {
        return params.getJavaAgents().getFiles().stream()
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
    @JsonSerialize(as = ImmutableLaunchConfigInfo.class)
    @JsonDeserialize(as = ImmutableLaunchConfigInfo.class)
    public interface LaunchConfigInfo {
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

        final class Builder extends ImmutableLaunchConfigInfo.Builder {}
    }

    private LaunchConfig() {}
}
