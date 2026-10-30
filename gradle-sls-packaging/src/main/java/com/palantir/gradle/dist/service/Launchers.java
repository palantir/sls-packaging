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
import com.palantir.gradle.dist.service.tasks.CreateJavaLauncherScriptsTask;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.immutables.value.Value.Immutable;

/**
 * Wires the launcher used by {@code init.sh}: the go-java-launcher binaries (default), or a java-based replacement
 * configured through {@link JavaLauncherExtension}.
 * <p>
 * Both launchers are registered, and {@link #configure} returns a provider selecting one of them. Consumers only
 * use the selected launcher, so the other one is never resolved nor built.
 */
final class Launchers {
    // Used as fallback version if no higher version is specified in 'versions.props'.
    private static final String FALLBACK_GO_JAVA_VERSION = "1.215.0";
    private static final String GO_JAVA_LAUNCHER = "com.palantir.launching:go-java-launcher";
    private static final String GO_INIT = "com.palantir.launching:go-init";
    /** The name of the Gradle configuration used to provide the java launcher libraries. */
    private static final String JAVA_LAUNCHER_CONFIGURATION = "javaLauncher";

    /** Files a launcher contributes to the distribution. */
    record LauncherConfiguration(LauncherType type, Provider<?> bin, Provider<?> lib) {}

    static Provider<LauncherConfiguration> configure(
            Project project, JavaServiceDistributionExtension distributionExtension) {
        JavaLauncherExtension javaLauncher = distributionExtension.getJavaLauncher();
        NamedDomainObjectProvider<Configuration> javaLauncherConfig = project.getConfigurations()
                .register(JAVA_LAUNCHER_CONFIGURATION, conf -> {
                    conf.setDescription(
                            "Resolves the java-based launcher set by `distribution.javaLauncher.launcher`.");
                    conf.setCanBeConsumed(false);
                    conf.getDependencies()
                            .addAllLater(javaLauncher
                                    .getLauncher()
                                    .map(notation ->
                                            List.of(project.getDependencies().create(notation))));
                });
        // Resolve the launcher with the rest of the dependencies, as for the asset configuration
        project.getRootProject().getPlugins().withId("com.palantir.consistent-versions", _plugin -> {
            GcvUtils.lockConfigurationInGcv(project, JAVA_LAUNCHER_CONFIGURATION);
        });

        LauncherConfiguration go = configureGo(project);
        LauncherConfiguration java = configureJava(project, distributionExtension, javaLauncherConfig);

        Provider<Boolean> enabled = javaLauncherEnabled(distributionExtension);

        javaLauncher.getLauncherCommand().set(launcherCommand(distributionExtension, enabled, javaLauncherConfig));
        javaLauncher.getLauncherCommand().disallowChanges();

        // Consumers flatMap into the selected launcher's providers, which carry the tasks producing its files
        return enabled.map(useJavaLauncher -> useJavaLauncher ? java : go);
    }

    @Immutable
    interface JavaLauncherInputs {
        boolean launcherSet();

        boolean jdkBundled();

        String launcherMainClass();

        String initMainClass();
    }

    /**
     * Whether the java-based launcher is enabled, which is the case when a launcher is set.
     * <p>
     * Throws upon misconfiguration (e.g. missing main classes).
     */
    private static Provider<Boolean> javaLauncherEnabled(JavaServiceDistributionExtension distributionExtension) {
        JavaLauncherExtension javaLauncher = distributionExtension.getJavaLauncher();
        return javaLauncher
                .getLauncher()
                .map(_notation -> true)
                .orElse(false)
                .map(launcherSet -> ImmutableJavaLauncherInputs.builder().launcherSet(launcherSet))
                .zip(
                        distributionExtension.isJdkBundled().orElse(false),
                        ImmutableJavaLauncherInputs.Builder::jdkBundled)
                .zip(
                        javaLauncher.getLauncherMainClass().orElse(""),
                        ImmutableJavaLauncherInputs.Builder::launcherMainClass)
                .zip(javaLauncher.getInitMainClass().orElse(""), ImmutableJavaLauncherInputs.Builder::initMainClass)
                .map(ImmutableJavaLauncherInputs.Builder::build)
                .map(Launchers::validate);
    }

    private static boolean validate(JavaLauncherInputs inputs) {
        if (!inputs.launcherSet()) {
            return false;
        }
        if (!inputs.jdkBundled()) {
            throw new GradleException(
                    "Jdks must be configured and included in the distribution to use the java-based launcher.");
        }
        if (Strings.isNullOrEmpty(inputs.launcherMainClass())) {
            throw new GradleException("'distribution.javaLauncher.launcherMainClass' must be specified when the"
                    + " java-based launcher is set");
        }
        if (Strings.isNullOrEmpty(inputs.initMainClass())) {
            throw new GradleException("'distribution.javaLauncher.initMainClass' must be specified when the"
                    + " java-based launcher is set");
        }
        return true;
    }

    /**
     * The command running the launcher main class, using locations relative to the distribution root directory.
     * Absent unless the java-based launcher is enabled.
     */
    private static Provider<List<String>> launcherCommand(
            JavaServiceDistributionExtension distributionExtension,
            Provider<Boolean> enabled,
            NamedDomainObjectProvider<Configuration> javaLauncherConfig) {
        JavaLauncherExtension javaLauncher = distributionExtension.getJavaLauncher();
        Provider<String> classpath = javaLauncherConfig
                .flatMap(Configuration::getElements)
                .map(files -> files.stream()
                        .map(file -> "service/lib/" + file.getAsFile().getName())
                        .collect(Collectors.joining(":")));
        return enabled
                // A null mapping result makes the provider absent
                .map(useJavaLauncher -> useJavaLauncher ? ImmutableList.<String>builder() : null)
                .zip(distributionExtension.getJavaHome(), (command, javaHome) -> command.add(javaHome + "/bin/java"))
                .zip(javaLauncher.getJvmArgs(), ImmutableList.Builder::addAll)
                .zip(classpath, (command, cp) -> command.add("-cp", cp))
                .zip(
                        javaLauncher.getLauncherMainClass(),
                        (command, mainClass) -> command.add(mainClass).build());
    }

    private static LauncherConfiguration configureGo(Project project) {
        NamedDomainObjectProvider<Configuration> launcherConfig =
                registerGoConfiguration(project, "goJavaLauncherBinary", GO_JAVA_LAUNCHER);
        NamedDomainObjectProvider<Configuration> initConfig = registerGoConfiguration(project, "goInitBinary", GO_INIT);

        // Note: we were previously using a Copy task, which was copying the binaries directly into the scripts
        // directory. This is a bit better, as we now have a dedicated directory, which we then copy into the scripts
        // directory in the DistTar task
        TaskProvider<Sync> copyLauncherBinaries = project.getTasks()
                .register("copyLauncherBinaries", Sync.class, task -> {
                    task.from(untar(project, launcherConfig));
                    task.from(untar(project, initConfig));
                    task.into(project.getLayout().getBuildDirectory().dir("launcher/go"));
                    task.eachFile(fcd -> {
                        String[] segments = fcd.getRelativePath().getSegments();
                        fcd.setRelativePath(new RelativePath(
                                !fcd.getFile().isDirectory(), Arrays.copyOfRange(segments, 3, segments.length)));
                    });
                });

        return new LauncherConfiguration(
                LauncherType.GO, copyLauncherBinaries.map(Sync::getDestinationDir), project.provider(project::files));
    }

    private static NamedDomainObjectProvider<Configuration> registerGoConfiguration(
            Project project, String name, String coordinate) {
        NamedDomainObjectProvider<Configuration> configuration =
                project.getConfigurations().register(name);
        project.getDependencies()
                .addProvider(name, goJavaLauncherVersion(project).map(v -> coordinate + ":" + v));
        return configuration;
    }

    /** To make our unit-test setup simpler, we allow hard-coding a specific go-java-launcher fallback version. */
    private static Provider<String> goJavaLauncherVersion(Project project) {
        return project.getProviders()
                .gradleProperty(JavaServiceDistributionPlugin.TEST_GO_JAVA_LAUNCHER_FALLBACK_VERSION_OVERRIDE)
                .map(versionOverride -> {
                    project.getLogger()
                            .lifecycle("using test only version override for go-java-launcher: {}", versionOverride);
                    return versionOverride;
                })
                .orElse(FALLBACK_GO_JAVA_VERSION);
    }

    private static Provider<FileTree> untar(Project project, NamedDomainObjectProvider<Configuration> configuration) {
        return configuration.map(conf -> project.tarTree(conf.getSingleFile()));
    }

    private static LauncherConfiguration configureJava(
            Project project,
            JavaServiceDistributionExtension distributionExtension,
            NamedDomainObjectProvider<Configuration> javaLauncherConfig) {
        TaskProvider<CreateJavaLauncherScriptsTask> createJavaLauncherScripts = project.getTasks()
                .register("createJavaLauncherScripts", CreateJavaLauncherScriptsTask.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates scripts running the java-based launcher.");
                    task.getBundledJdk().set(distributionExtension.isJdkBundled());
                    task.getJavaHome().set(distributionExtension.getJavaHome());

                    task.getClasspath().from(javaLauncherConfig);

                    JavaLauncherExtension javaLauncher = distributionExtension.getJavaLauncher();
                    task.getLauncherMainClass().set(javaLauncher.getLauncherMainClass());
                    task.getInitMainClass().set(javaLauncher.getInitMainClass());
                    task.getJvmArgs().set(javaLauncher.getJvmArgs());
                });

        return new LauncherConfiguration(
                LauncherType.JAVA,
                createJavaLauncherScripts.flatMap(CreateJavaLauncherScriptsTask::getOutputDir),
                javaLauncherConfig);
    }

    private Launchers() {}
}
