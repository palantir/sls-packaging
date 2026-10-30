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

import com.palantir.gradle.dist.service.tasks.CreateJavaLauncherScriptsTask;
import java.util.Arrays;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RelativePath;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

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

    /**
     * Files a launcher contributes to the distribution.
     * <p>
     * Also provides the task used to generate these files, so it can be wired (this appears to not always be working
     * properly from just using the file collections, seemingly due to the use of
     * {@link Provider#zip} in {@link JavaLauncherExtension#fullyConfigured()}).
     */
    record LauncherConfiguration(LauncherType type, FileCollection bin, FileCollection lib) {}

    static Provider<LauncherConfiguration> configure(
            Project project, JavaServiceDistributionExtension distributionExtension) {
        LauncherConfiguration go = configureGo(project);
        LauncherConfiguration java = configureJava(project, distributionExtension);

        return distributionExtension.getJavaLauncher().fullyConfigured().map(configured -> configured ? java : go);
        // This instead of a .map allows to properly wire up task dependencies using the file collection outputs
        //        return project.provider(
        //                () -> distributionExtension.getJavaLauncher().fullyConfigured().get() ? java : go);
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

        return new LauncherConfiguration(LauncherType.GO, project.files(copyLauncherBinaries), project.files());
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
            Project project, JavaServiceDistributionExtension distributionExtension) {
        // Populated by plugins providing a java-based launcher
        NamedDomainObjectProvider<Configuration> javaLauncherConfig = project.getConfigurations()
                .register(JavaLauncherExtension.JAVA_LAUNCHER_CONFIGURATION, conf -> {
                    conf.setDescription(
                            "Java-based replacement for go-java-launcher/go-init, see `distribution.javaLauncher`.");
                    conf.setCanBeConsumed(false);
                });

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
                LauncherType.JAVA, project.files(createJavaLauncherScripts), project.files(javaLauncherConfig));
    }

    private Launchers() {}
}
