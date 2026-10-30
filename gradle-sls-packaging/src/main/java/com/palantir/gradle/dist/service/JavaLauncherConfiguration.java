/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service;

import com.google.common.base.Strings;
import com.palantir.logsafe.Preconditions;
import groovy.lang.Tuple;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

/**
 * Configures a jvm based replacement for the <a href="https://github.com/palantir/go-java-launcher">go-java-launcher and go-init binaries</a>
 * which are packaged in distributions by default.
 * <p>
 * When all the extension properties are set, the go binaries are neither downloaded nor packaged, and
 * {@code service/bin/init.sh} invokes the configured main classes with the bundled jdk instead.
 * <p>
 * This requires the runtime JDK to be bundled by being provided in {@link JavaServiceDistributionExtension#getJdks()}.
 * If it is not provided, and this extension is configured, an exception will be thrown.
 */
public abstract class JavaLauncherConfiguration {
    /** The name of the Gradle configuration used to provide the java launcher libraries. */
    static final String JAVA_LAUNCHER_CONFIGURATION = "javaLauncher";

    /** Main class which behaves like the {@code go-java-launcher} binary. */
    public abstract Property<String> getLauncherMainClass();

    /** Main class which behaves like the {@code go-init} binary. */
    public abstract Property<String> getInitMainClass();

    /** Options to pass to the jvm when starting the launcher. */
    public abstract ListProperty<String> getJvmArgs();

    public final void launcherMainClass(String launcherMainClass) {
        Preconditions.checkArgument(
                SourceVersion.isName(launcherMainClass), "launcherMainClass must be a valid class name");
        getLauncherMainClass().set(launcherMainClass);
    }

    public final void initMainClass(String initMainClass) {
        Preconditions.checkArgument(SourceVersion.isName(initMainClass), "initMainClass must be a valid class name");
        getInitMainClass().set(initMainClass);
    }

    /**
     * Utility method returning whether the java-based launcher is configured.
     * <p>
     * Throws upon misconfiguration (e.g. partial configuration of the java-based launcher).
     */
    public static Provider<Boolean> javaLauncherConfigured(
            Project project, JavaServiceDistributionExtension extension) {
        Provider<Boolean> bundledJdk = extension.isJdkBundled().orElse(false);

        Provider<Configuration> javaLauncherConfig =
                project.getConfigurations().named(JAVA_LAUNCHER_CONFIGURATION).orElse((Configuration) null);

        JavaLauncherConfiguration javaLauncher = extension.getJavaLauncher();
        Provider<String> launcherMainClass = javaLauncher.getLauncherMainClass().orElse("");
        Provider<String> initMainClass = javaLauncher.getInitMainClass().orElse("");

        return bundledJdk
                .zip(javaLauncherConfig, Tuple::tuple)
                .zip(launcherMainClass, (t, s) -> Tuple.tuple(t.getV1(), t.getV2(), s))
                .zip(initMainClass, (t, s) -> Tuple.tuple(t.getV1(), t.getV2(), t.getV3(), s))
                .map(t -> {
                    boolean jdkIsBundled = t.getV1();
                    Configuration config = t.getV2();

                    // It seems under certain conditions the configuration inherits dependencies like project(:)
                    // This makes getAllDependencies not be empty
                    // What we mostly care about here is whether javaLauncher was directly configured
                    // so we use getDependencies (which does not resolve the configuration)
                    boolean jarConfigured =
                            config != null && !config.getDependencies().isEmpty();
                    boolean launcherMainClassConfigured = !Strings.isNullOrEmpty(t.getV3());
                    boolean initMainClassConfigured = !Strings.isNullOrEmpty(t.getV4());
                    if (jarConfigured) {
                        if (!jdkIsBundled) {
                            throw new GradleException(
                                    "Jdks must be configured and included in the distribution to configure a java-based"
                                            + " launcher.");
                        }
                        if (!launcherMainClassConfigured) {
                            throw new GradleException(
                                    "'distribution.javaLauncher.launcherMainClass' must be specified when a java-based"
                                            + " launcher is configured");
                        }
                        if (!initMainClassConfigured) {
                            throw new GradleException(
                                    "'distribution.javaLauncher.initMainClass' must be specified when a java-based"
                                            + " launcher is configured");
                        }
                    } else if (launcherMainClassConfigured || initMainClassConfigured) {
                        throw new GradleException(
                                "Java-based launcher main classes have been configured, but no library was defined for"
                                        + " it. Configure a launcher with the " + JAVA_LAUNCHER_CONFIGURATION
                                        + " configuration.");
                    }

                    // At this point, we know launcher and init are set iff config is set
                    // but adding all three to be clear we want all three to be set
                    return jarConfigured && launcherMainClassConfigured && initMainClassConfigured;
                });
    }

    /**
     * Builds the command to run the {@link JavaLauncherConfiguration#getLauncherMainClass()} java launcher,
     * using locations relative to the distribution root directory.
     */
    public static Provider<List<String>> buildLauncherCommand(
            Project project, JavaServiceDistributionExtension distributionExtension) {
        JavaLauncherConfiguration javaLauncher = distributionExtension.getJavaLauncher();
        Provider<String> javaHome = distributionExtension.getJavaHome();
        Provider<List<String>> jvmArgs = javaLauncher.getJvmArgs();
        Provider<Set<File>> classpath =
                project.getConfigurations().named(JAVA_LAUNCHER_CONFIGURATION).map(Configuration::getFiles);
        Provider<String> mainClass = javaLauncher.getLauncherMainClass();

        Provider<Stream<String>> command = javaHome.map(jh -> Stream.of(jh + "/bin/java"));

        command = command.zip(jvmArgs, (cmd, args) -> Stream.concat(cmd, args.stream()));

        command = command.zip(
                classpath,
                (cmd, cp) -> Stream.concat(
                        cmd,
                        Stream.of(
                                "-cp",
                                cp.stream()
                                        .map(f -> "service/lib/" + f.getName())
                                        .collect(Collectors.joining(":")))));

        command = command.zip(mainClass, (cmd, clazz) -> Stream.concat(cmd, Stream.of(clazz)));

        return command.map(Stream::toList);
    }
}
