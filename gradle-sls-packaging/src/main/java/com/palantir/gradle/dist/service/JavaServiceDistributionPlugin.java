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
package com.palantir.gradle.dist.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.palantir.gradle.dist.ProductDependencyIntrospectionPlugin;
import com.palantir.gradle.dist.SlsBaseDistPlugin;
import com.palantir.gradle.dist.asset.AssetDistributionPlugin;
import com.palantir.gradle.dist.service.tasks.CreateCheckScriptTask;
import com.palantir.gradle.dist.service.tasks.CreateInitScriptTask;
import com.palantir.gradle.dist.service.tasks.LaunchConfigTask;
import com.palantir.gradle.dist.service.tasks.LazyCreateStartScriptTask;
import com.palantir.gradle.dist.service.util.MainClassResolver;
import com.palantir.gradle.dist.tasks.ConfigTarTask;
import com.palantir.gradle.dist.tasks.CreateManifestTask;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RelativePath;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.util.GradleVersion;

public final class JavaServiceDistributionPlugin implements Plugin<Project> {
    // Used as fallback version if no higher version is specified in 'versions.props'.
    private static final String FALLBACK_GO_JAVA_VERSION = "1.18.0";
    private static final String GO_JAVA_LAUNCHER = "com.palantir.launching:go-java-launcher";
    private static final String GO_INIT = "com.palantir.launching:go-init";
    public static final String GROUP_NAME = "Distribution";

    @VisibleForTesting
    static final String TEST_GO_JAVA_LAUNCHER_FALLBACK_VERSION_OVERRIDE = "testOnlyGoJavaLauncherFallbackVersion";

    @Override
    @SuppressWarnings({"checkstyle:methodlength", "RawTypes", "deprecation"})
    public void apply(Project project) {
        project.getPluginManager().apply(SlsBaseDistPlugin.class);
        if (project.getPlugins().hasPlugin(AssetDistributionPlugin.class)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-asset-distribution' and "
                    + "'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.");
        }
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(ProductDependencyIntrospectionPlugin.class);
        project.getPluginManager().apply(DiagnosticsManifestPlugin.class);
        JavaServiceDistributionExtension distributionExtension =
                project.getExtensions().create("distribution", JavaServiceDistributionExtension.class, project);

        // In baseline 3.52.0 we added this new plugin as a one-liner to add the --enable-preview flag wherever
        // necessary (https://github.com/palantir/gradle-baseline/pull/1549). We're using the extraProperties thing to
        // avoid taking a compile dependency on baseline.
        project.getPlugins().withId("com.palantir.baseline-enable-preview-flag", _withId -> {
            distributionExtension.defaultJvmOpts(project.provider(() -> {
                Provider<Boolean> enablePreview = (Provider<Boolean>) project.getExtensions()
                        .getExtraProperties()
                        .getProperties()
                        .get("enablePreview");
                return enablePreview.get() ? Collections.singletonList("--enable-preview") : Collections.emptyList();
            }));
        });

        Configuration runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");
        Configuration javaAgentConfiguration = project.getConfigurations().create("javaAgent");

        // Set default configuration to look for product dependencies to be runtimeClasspath
        distributionExtension.setProductDependenciesConfig(runtimeClasspath);

        Provider<String> mainClassName = distributionExtension
                .getMainClass()
                .orElse(project.provider(() -> MainClassResolver.resolveMainClass(project)));

        // Create configuration to load executable dependencies
        Configuration launcherConfig = project.getConfigurations().create("goJavaLauncherBinary");
        project.getDependencies().add(launcherConfig.getName(), getGoJavaLauncherCoordinate(project, GO_JAVA_LAUNCHER));
        Configuration initConfig = project.getConfigurations().create("goInitBinary");
        project.getDependencies().add(initConfig.getName(), getGoJavaLauncherCoordinate(project, GO_INIT));

        TaskProvider<Copy> copyLauncherBinaries = project.getTasks()
                .register("copyLauncherBinaries", Copy.class, task -> {
                    task.from(project.provider(() -> project.tarTree(launcherConfig.getSingleFile())));
                    task.from(project.provider(() -> project.tarTree(initConfig.getSingleFile())));
                    task.into(project.getLayout().getBuildDirectory().dir("scripts"));
                    task.eachFile(fcd -> {
                        String[] segments = fcd.getRelativePath().getSegments();
                        fcd.setRelativePath(new RelativePath(
                                !fcd.getFile().isDirectory(), Arrays.copyOfRange(segments, 3, segments.length)));
                    });
                });

        TaskProvider<Jar> manifestClassPathTask = project.getTasks()
                .register("manifestClasspathJar", Jar.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Creates a jar containing a Class-Path manifest entry specifying the classpath "
                            + "using pathing jar rather than command line argument on Windows, since Windows path "
                            + "sizes are limited.");
                    task.getArchiveAppendix().set("manifest-classpath");

                    task.doFirst(new Action<Task>() {
                        @Override
                        public void execute(Task _task) {
                            FileCollection runtimeClasspath =
                                    project.getConfigurations().getByName("runtimeClasspath");

                            FileCollection jarOutputs = project.getTasks()
                                    .withType(Jar.class)
                                    .getByName(JavaPlugin.JAR_TASK_NAME)
                                    .getOutputs()
                                    .getFiles();

                            String classPath = runtimeClasspath.plus(jarOutputs).getFiles().stream()
                                    .map(File::getName)
                                    .collect(Collectors.joining(" "));
                            task.getManifest()
                                    .getAttributes()
                                    .put(
                                            "Class-Path",
                                            classPath
                                                    + " "
                                                    + task.getArchiveFileName().get());
                        }
                    });
                    task.onlyIf(_unused ->
                            distributionExtension.getEnableManifestClasspath().get());
                });

        TaskProvider<LazyCreateStartScriptTask> startScripts = project.getTasks()
                .register("createStartScripts", LazyCreateStartScriptTask.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates standard Java start scripts.");
                    task.setOutputDir(new File(project.getBuildDir(), "scripts"));
                    // Since we write out the name of this task's output (when it's enabled), we should depend on it
                    task.dependsOn(manifestClassPathTask);
                    task.getLazyMainClassName().set(mainClassName);

                    if (distributionExtension.getEnableManifestClasspath().get()) {
                        task.doLast(new Action<Task>() {
                            @Override
                            public void execute(Task _task) {
                                try {
                                    replaceManifestClasspath(
                                            task.getWindowsScript().toPath(),
                                            manifestClassPathTask
                                                    .get()
                                                    .getArchiveFileName()
                                                    .get());
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }
                        });
                    }
                });

        TaskProvider<Jar> jarTask = project.getTasks().withType(Jar.class).named(JavaPlugin.JAR_TASK_NAME);

        // HACKHACK all fields of CreateStartScript are eager so we configure the task after evaluation to
        // ensure everything has been correctly configured
        project.afterEvaluate(_p -> startScripts.configure(task -> {
            task.setApplicationName(
                    distributionExtension.getDistributionServiceName().get());
            task.setDefaultJvmOpts(distributionExtension.getDefaultJvmOpts().get());
            task.dependsOn(manifestClassPathTask);

            // TODO(fwindheuser): Replace 'JavaPluginConvention' with 'JavaPluginExtension' before moving to Gradle 8.
            org.gradle.api.plugins.JavaPluginConvention javaPlugin =
                    project.getConvention().findPlugin(org.gradle.api.plugins.JavaPluginConvention.class);
            if (distributionExtension.getEnableManifestClasspath().get()) {
                task.setClasspath(manifestClassPathTask.get().getOutputs().getFiles());
            } else {
                task.setClasspath(jarTask.get()
                        .getOutputs()
                        .getFiles()
                        .plus(javaPlugin.getSourceSets().getByName("main").getRuntimeClasspath()));
            }
        }));

        TaskProvider<LaunchConfigTask> launchConfigTask = project.getTasks()
                .register("createLaunchConfig", LaunchConfigTask.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates launcher-static.yml and launcher-check.yml configurations.");
                    task.dependsOn(manifestClassPathTask);

                    task.getMainClass().set(mainClassName);
                    task.getServiceName().set(distributionExtension.getDistributionServiceName());
                    task.getArgs().set(distributionExtension.getArgs());
                    task.getCheckArgs().set(distributionExtension.getCheckArgs());
                    task.getGcJvmOptions().set(distributionExtension.getGcJvmOptions());
                    task.getDefaultJvmOpts().set(distributionExtension.getDefaultJvmOpts());
                    task.getAddJava8GcLogging().set(distributionExtension.getAddJava8GcLogging());
                    task.getJavaHome().set(distributionExtension.getJavaHome());
                    task.getJavaVersion().set(distributionExtension.getJavaVersion());
                    task.getEnv().set(userConfiguredEnvWithJdkEnvVars(distributionExtension));
                });

        TaskProvider<CreateInitScriptTask> initScript = project.getTasks()
                .register("createInitScript", CreateInitScriptTask.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates daemonizing init.sh script.");
                    task.getServiceName().set(distributionExtension.getDistributionServiceName());
                });

        TaskProvider<CreateCheckScriptTask> checkScript = project.getTasks()
                .register("createCheckScript", CreateCheckScriptTask.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates healthcheck (service/monitoring/bin/check.sh) script.");
                    task.getServiceName().set(distributionExtension.getDistributionServiceName());
                    task.getCheckArgs().set(distributionExtension.getCheckArgs());
                });

        TaskProvider<CreateManifestTask> manifest =
                CreateManifestTask.createManifestTask(project, distributionExtension);

        TaskProvider<ConfigTarTask> configTar = ConfigTarTask.createConfigTarTask(project, distributionExtension);
        configTar.configure(task -> {
            task.from(launchConfigTask.flatMap(LaunchConfigTask::getStaticLauncher), copySpec -> {
                copySpec.into(DistTarTask.SCRIPTS_DIST_LOCATION);
            });
            task.dependsOn(manifest, launchConfigTask, startScripts, copyLauncherBinaries);
        });

        TaskProvider<JavaExec> runTask = project.getTasks().register("run", JavaExec.class, task -> {
            task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
            task.setDescription("Runs the specified project using configured mainClass and with default args.");
            task.dependsOn("jar");
            task.dependsOn(javaAgentConfiguration);
            task.getJvmArgumentProviders().add(new CommandLineArgumentProvider() {
                @Override
                public Iterable<String> asArguments() {
                    return ImmutableList.<String>builder()
                            .addAll(distributionExtension.getDefaultJvmOpts().get())
                            .addAll(distributionExtension.getGcJvmOptions().get())
                            .addAll(Collections2.transform(
                                    javaAgentConfiguration.getFiles(), file -> "-javaagent:" + file.getAbsolutePath()))
                            .build();
                }
            });
            if (GradleVersion.current().compareTo(GradleVersion.version("6.4")) < 0) {
                task.doFirst(new Action<Task>() {
                    @Override
                    public void execute(Task _task) {
                        task.setMain(mainClassName.get());
                    }
                });
            } else {
                task.getMainClass().set(mainClassName);
            }
        });

        // HACKHACK setClasspath of JavaExec is eager so we configure it after evaluation to ensure everything has
        // been correctly configured
        project.afterEvaluate(p -> runTask.configure(task -> {
            task.setClasspath(project.files(
                    jarTask.get().getArchiveFile().get(), p.getConfigurations().getByName("runtimeClasspath")));
            task.setArgs(distributionExtension.getArgs().get());
        }));

        TaskProvider<Tar> distTar = project.getTasks().register("distTar", Tar.class, task -> {
            task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
            task.setDescription("Creates a compressed, gzipped tar file that contains required runtime resources.");
            // Set compression in constructor so that task output has the right name from the start.
            task.setCompression(Compression.GZIP);
            task.getArchiveExtension().set("sls.tgz");
            task.dependsOn(
                    startScripts,
                    initScript,
                    checkScript,
                    copyLauncherBinaries,
                    launchConfigTask,
                    manifest,
                    manifestClassPathTask,
                    javaAgentConfiguration);
        });

        project.afterEvaluate(_p -> launchConfigTask.configure(task -> {
            task.getJavaAgents().setFrom(javaAgentConfiguration);
            FileCollection fullClasspath =
                    jarTask.get().getOutputs().getFiles().plus(distributionExtension.getProductDependenciesConfig());
            task.getFullClasspath().from(fullClasspath);
            task.getClasspath()
                    .from(
                            distributionExtension.getEnableManifestClasspath().get()
                                    ? manifestClassPathTask.get().getOutputs().getFiles()
                                    : fullClasspath);
        }));

        project.afterEvaluate(_proj -> distTar.configure(task -> {
            DistTarTask.configure(project, task, distributionExtension, jarTask);
        }));

        project.getArtifacts().add(SlsBaseDistPlugin.SLS_CONFIGURATION_NAME, distTar);
    }

    private static Provider<Map<String, String>> userConfiguredEnvWithJdkEnvVars(
            JavaServiceDistributionExtension distributionExtension) {

        return distributionExtension.getEnv().zip(distributionExtension.getJdks(), (userConfiguredEnv, jdks) -> {
            Map<String, String> actualEnv = new LinkedHashMap<>(userConfiguredEnv);

            jdks.keySet().stream().sorted().forEach(javaVersion -> {
                actualEnv.put(
                        "JAVA_" + javaVersion.getMajorVersion() + "_HOME",
                        distributionExtension.jdkPathInDist(javaVersion));
            });

            return Collections.unmodifiableMap(actualEnv);
        });
    }

    private static void replaceManifestClasspath(Path windowsScript, String manifestClassPathArchiveFileName)
            throws IOException {
        // Replace standard classpath with pathing jar in order to circumnavigate length limits:
        // https://issues.gradle.org/browse/GRADLE-2992
        String winFileText = Files.readString(windowsScript);

        // Remove too-long-classpath and use pathing jar instead
        String cleanedText = winFileText
                .replaceAll("set CLASSPATH=.*", "rem CLASSPATH declaration removed.")
                .replaceAll(
                        "(\"%JAVA_EXE%\" .* -classpath \")%CLASSPATH%(\" .*)",
                        "$1%APP_HOME%\\\\lib\\\\" + manifestClassPathArchiveFileName + "$2");

        Files.writeString(windowsScript, cleanedText);
    }

    /** To make our unit-test setup simpler, we allow hard-coding a specific go-java-launcher fallback version. */
    private static String getGoJavaLauncherCoordinate(Project project, String coordinate) {
        if (!project.hasProperty(TEST_GO_JAVA_LAUNCHER_FALLBACK_VERSION_OVERRIDE)) {
            return coordinate + ":" + FALLBACK_GO_JAVA_VERSION;
        }
        String versionOverride = project.property(TEST_GO_JAVA_LAUNCHER_FALLBACK_VERSION_OVERRIDE)
                .toString();
        project.getLogger().lifecycle("using test only version override for go-java-launcher: {}", versionOverride);
        return coordinate + ":" + versionOverride;
    }
}
