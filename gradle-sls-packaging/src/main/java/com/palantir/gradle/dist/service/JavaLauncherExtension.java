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
import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.Preconditions;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.immutables.value.Value.Immutable;

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
public class JavaLauncherExtension {
    /** The name of the Gradle configuration used to provide the java launcher libraries. */
    static final String JAVA_LAUNCHER_CONFIGURATION = "javaLauncher";

    private final Provider<Boolean> isJdkBundled;
    private final Provider<String> javaHome;
    private final Provider<Configuration> javaLauncherConfiguration;

    private final Property<String> launcherMainClass;
    private final Property<String> initMainClass;
    private final ListProperty<String> jvmArgs;

    public JavaLauncherExtension(Project project, JavaServiceDistributionExtension distributionExtension) {
        ObjectFactory objectFactory = project.getObjects();

        //        JavaServiceDistributionExtension distributionExtension =
        //                project.getExtensions().findByType(JavaServiceDistributionExtension.class);
        Preconditions.checkNotNull(distributionExtension, "Could not find Java service distribution extension");
        javaHome = distributionExtension.getJavaHome();
        isJdkBundled = distributionExtension.isJdkBundled();

        // Hack for this to be lazy?
        javaLauncherConfiguration = project.provider(
                        () -> project.getConfigurations().named(JAVA_LAUNCHER_CONFIGURATION))
                .map(Provider::get);

        launcherMainClass = objectFactory.property(String.class);
        initMainClass = objectFactory.property(String.class);

        jvmArgs = objectFactory.listProperty(String.class).empty();
    }

    /** Main class which behaves like the {@code go-java-launcher} binary. */
    public Provider<String> getLauncherMainClass() {
        return launcherMainClass;
    }

    public final void launcherMainClass(String clazz) {
        Preconditions.checkArgument(SourceVersion.isName(clazz), "launcherMainClass must be a valid class name");
        launcherMainClass.set(launcherMainClass);
    }

    /** Main class which behaves like the {@code go-init} binary. */
    public Provider<String> getInitMainClass() {
        return initMainClass;
    }

    public final void initMainClass(String clazz) {
        Preconditions.checkArgument(SourceVersion.isName(clazz), "initMainClass must be a valid class name");
        initMainClass.set(clazz);
    }

    /** Options to pass to the jvm when starting the launcher. */
    public Provider<List<String>> getJvmArgs() {
        return jvmArgs;
    }

    public final void jvmArgs(List<String> args) {
        jvmArgs.addAll(args);
    }

    public final Provider<List<String>> classpath() {
        return javaLauncherConfiguration
                .map(Configuration::getFiles)
                .map(cp -> cp.stream().map(f -> "service/lib/" + f.getName()).collect(Collectors.toList()));
    }

    @Immutable
    interface JavaLauncherConfig {
        boolean isJdkBundled();

        DependencySet dependencies();

        String launcherMainClass();

        String initMainClass();
    }

    /**
     * Utility method returning whether the java-based launcher is configured.
     * <p>
     * Throws upon misconfiguration (e.g. partial configuration of the java-based launcher).
     */
    public final Provider<Boolean> fullyConfigured() {
        Provider<ImmutableJavaLauncherConfig.Builder> builder = isJdkBundled
                .orElse(false)
                .map(b -> ImmutableJavaLauncherConfig.builder().isJdkBundled(b));

        builder = builder.zip(javaLauncherConfiguration, (b, c) -> {
            // It seems under certain conditions the configuration inherits dependencies like project(:)
            // This makes getAllDependencies not be empty
            // What we mostly care about here is whether javaLauncher was directly configured
            // so we use getDependencies (which does not resolve the configuration)
            return b.dependencies(c.getDependencies());
        });

        builder = builder.zip(launcherMainClass.orElse(""), ImmutableJavaLauncherConfig.Builder::launcherMainClass);
        builder = builder.zip(initMainClass.orElse(""), ImmutableJavaLauncherConfig.Builder::initMainClass);

        return builder.map(ImmutableJavaLauncherConfig.Builder::build).map(config -> {
            boolean jarConfigured = !config.dependencies().isEmpty();
            boolean launcherMainClassConfigured = !Strings.isNullOrEmpty(config.launcherMainClass());
            boolean initMainClassConfigured = !Strings.isNullOrEmpty(config.initMainClass());
            if (jarConfigured) {
                if (!config.isJdkBundled()) {
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
     * Builds the command to run the {@link JavaLauncherExtension#getLauncherMainClass()} java launcher,
     * using locations relative to the distribution root directory.
     */
    public final Provider<List<String>> launcherCommand() {
        Provider<ImmutableList.Builder<String>> result =
                javaHome.map(jh -> ImmutableList.<String>builder().add(jh + "/bin/java"));

        result = result.zip(jvmArgs, ImmutableList.Builder::addAll);
        result = result.zip(classpath(), (b, cp) -> {
            b.add("-cp");
            b.add(String.join(":", cp));
            return b;
        });

        result = result.zip(launcherMainClass, ImmutableList.Builder::add);

        return result.map(ImmutableList.Builder::build);
    }
}
