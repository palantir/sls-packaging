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

import com.palantir.gradle.dist.ProductDependencyIntrospectionPlugin;
import com.palantir.gradle.dist.SlsBaseDistPlugin;
import com.palantir.gradle.dist.asset.AssetDistributionPlugin;
import com.palantir.gradle.dist.pod.PodDistributionPlugin;
import com.palantir.gradle.dist.service.tasks.CopyLauncherBinariesTask;
import com.palantir.gradle.dist.service.tasks.CopyYourkitAgentTask;
import com.palantir.gradle.dist.service.tasks.CopyYourkitLicenseTask;
import com.palantir.gradle.dist.service.tasks.CreateCheckScriptTask;
import com.palantir.gradle.dist.service.tasks.CreateInitScriptTask;
import com.palantir.gradle.dist.service.tasks.DistTarTask;
import com.palantir.gradle.dist.service.tasks.LaunchConfigTask;
import com.palantir.gradle.dist.tasks.ConfigTarTask;
import com.palantir.gradle.dist.tasks.CreateManifestTask;
import java.io.File;
import java.util.stream.Collectors;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.application.CreateStartScripts;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.util.GFileUtils;

public final class JavaServiceDistributionPlugin implements Plugin<Project> {
    private static final String GO_JAVA_LAUNCHER_BINARIES = "goJavaLauncherBinaries";
    private static final String GO_JAVA_LAUNCHER = "com.palantir.launching:go-java-launcher:1.6.2";
    private static final String GO_INIT = "com.palantir.launching:go-init:1.6.2";
    public static final String GROUP_NAME = "Distribution";

    @SuppressWarnings("checkstyle:methodlength")
    public void apply(Project project) {
        project.getPluginManager().apply(SlsBaseDistPlugin.class);
        if (project.getPlugins().hasPlugin(AssetDistributionPlugin.class)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-asset-distribution' and "
                    + "'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.");
        }
        if (project.getPlugins().hasPlugin(PodDistributionPlugin.class)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-pod-distribution' and "
                    + "'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.");
        }
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(ProductDependencyIntrospectionPlugin.class);
        JavaServiceDistributionExtension distributionExtension = project.getExtensions().create(
                "distribution", JavaServiceDistributionExtension.class, project);

        // Set default configuration to look for product dependencies to be runtimeClasspath
        distributionExtension.setProductDependenciesConfig(project.getConfigurations().getByName("runtimeClasspath"));

        // Create configuration to load executable dependencies
        project.getConfigurations().maybeCreate(GO_JAVA_LAUNCHER_BINARIES);
        project.getDependencies().add(GO_JAVA_LAUNCHER_BINARIES, GO_JAVA_LAUNCHER);
        project.getDependencies().add(GO_JAVA_LAUNCHER_BINARIES, GO_INIT);

        TaskProvider<CopyLauncherBinariesTask> copyLauncherBinaries = project.getTasks()
                .register("copyLauncherBinaries", CopyLauncherBinariesTask.class);

        TaskProvider<Jar> manifestClassPathTask = project.getTasks().register(
                "manifestClasspathJar", Jar.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Creates a jar containing a Class-Path manifest entry specifying the classpath "
                            + "using pathing jar rather than command line argument on Windows, since Windows path "
                            + "sizes are limited.");
                    // TODO(forozco): Use provider based API when minimum version is 5.1
                    task.setAppendix("manifest-classpath");

                    task.doFirst(t -> {

                        FileCollection runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");

                        FileCollection jarOutputs = project.getTasks().withType(Jar.class)
                                .getByName(JavaPlugin.JAR_TASK_NAME).getOutputs().getFiles();

                        String classPath = runtimeClasspath.plus(jarOutputs).getFiles()
                                .stream()
                                .map(File::getName)
                                .collect(Collectors.joining(" "));
                        task.getManifest().getAttributes().put("Class-Path", classPath + " " + task.getArchiveName());
                    });
                    task.onlyIf(t -> distributionExtension.getEnableManifestClasspath().get());
                });

        TaskProvider<CreateStartScripts> startScripts = project.getTasks().register("createStartScripts",
                CreateStartScripts.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates standard Java start scripts.");
                    task.setOutputDir(new File(project.getBuildDir(), "scripts"));
                    // Since we write out the name of this task's output (when it's enabled), we should depend on it
                    task.dependsOn(manifestClassPathTask);

                    task.doLast(t -> {
                        if (distributionExtension.getEnableManifestClasspath().get()) {
                            // Replace standard classpath with pathing jar in order to circumnavigate length limits:
                            // https://issues.gradle.org/browse/GRADLE-2992
                            String winFileText = GFileUtils.readFile(task.getWindowsScript());

                            // Remove too-long-classpath and use pathing jar instead
                            String cleanedText = winFileText
                                    .replaceAll("set CLASSPATH=.*", "rem CLASSPATH declaration removed.")
                                    .replaceAll(
                                            "(\"%JAVA_EXE%\" .* -classpath \")%CLASSPATH%(\" .*)",
                                            "$1%APP_HOME%\\\\lib\\\\" + manifestClassPathTask.get().getArchiveName()
                                                    + "$2");

                            GFileUtils.writeFile(cleanedText, task.getWindowsScript());
                        }
                    });
                });

        TaskProvider<Jar> jarTask = project.getTasks().withType(Jar.class).named(JavaPlugin.JAR_TASK_NAME);

        // HACKHACK all fields of CreateStartScript are eager so we configure the task after evaluation to
        // ensure everything has been correctly configured
        project.afterEvaluate(p -> startScripts.configure(task -> {
            task.setMainClassName(distributionExtension.getMainClass().get());
            task.setApplicationName(distributionExtension.getDistributionServiceName().get());
            task.setDefaultJvmOpts(distributionExtension.getDefaultJvmOpts().get());

            JavaPluginConvention javaPlugin = project.getConvention().findPlugin(JavaPluginConvention.class);
            task.setClasspath(jarTask.get().getOutputs().getFiles().plus(
                    javaPlugin.getSourceSets().getByName("main").getRuntimeClasspath()));
        }));

        TaskProvider<LaunchConfigTask> launchConfigTask = project.getTasks().register(
                "createLaunchConfig", LaunchConfigTask.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates launcher-static.yml and launcher-check.yml configurations.");

                    task.getMainClass().set(distributionExtension.getMainClass());
                    task.getServiceName().set(distributionExtension.getDistributionServiceName());
                    task.getArgs().set(distributionExtension.getArgs());
                    task.getCheckArgs().set(distributionExtension.getCheckArgs());
                    task.getGc().set(distributionExtension.getGc());
                    task.getDefaultJvmOpts().set(distributionExtension.getDefaultJvmOpts());
                    task.getAddJava8GcLogging().set(distributionExtension.getAddJava8GcLogging());
                    task.getJavaHome().set(distributionExtension.getJavaHome());
                    task.getEnv().set(distributionExtension.getEnv());
                    task.setClasspath(
                            jarTask.get().getOutputs().getFiles().plus(
                                    distributionExtension.getProductDependenciesConfig()));
                });

        TaskProvider<CreateInitScriptTask> initScript = project.getTasks().register(
                "createInitScript", CreateInitScriptTask.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates daemonizing init.sh script.");
                    task.getServiceName().set(distributionExtension.getDistributionServiceName());
                });

        TaskProvider<CreateCheckScriptTask> checkScript = project.getTasks().register(
                "createCheckScript", CreateCheckScriptTask.class, task -> {
                    task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates healthcheck (service/monitoring/bin/check.sh) script.");
                    task.getServiceName().set(distributionExtension.getDistributionServiceName());
                    task.getCheckArgs().set(distributionExtension.getCheckArgs());
                });

        TaskProvider<CopyYourkitAgentTask> yourkitAgent = project.getTasks().register(
                "copyYourkitAgent", CopyYourkitAgentTask.class);
        TaskProvider<CopyYourkitLicenseTask> yourkitLicense = project.getTasks().register(
                "copyYourkitLicense", CopyYourkitLicenseTask.class);

        TaskProvider<CreateManifestTask> manifest = CreateManifestTask.createManifestTask(
                project, distributionExtension);

        TaskProvider<Tar> configTar = ConfigTarTask.createConfigTarTask(project, distributionExtension);
        configTar.configure(task -> task.dependsOn(manifest));


        TaskProvider<JavaExec> runTask = project.getTasks().register("run", JavaExec.class, task -> {
            task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
            task.setDescription("Runs the specified project using configured mainClass and with default args.");
            task.dependsOn("jar");
        });

        // HACKHACK setClasspath of JavaExec is eager so we configure it after evaluation to ensure everything has
        // been correctly configured
        project.afterEvaluate(p -> runTask.configure(task -> {
            task.setClasspath(project.files(
                    jarTask.get().getArchivePath(), p.getConfigurations().getByName("runtimeClasspath")));
            task.setMain(distributionExtension.getMainClass().get());
            task.setArgs(distributionExtension.getArgs().get());
            task.setJvmArgs(distributionExtension.getDefaultJvmOpts().get());
        }));

        TaskProvider<Tar> distTar = project.getTasks().register("distTar", Tar.class, task -> {
            task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
            task.setDescription("Creates a compressed, gzipped tar file that contains required runtime resources.");
            // Set compression in constructor so that task output has the right name from the start.
            task.setCompression(Compression.GZIP);
            task.setExtension("sls.tgz");

            task.dependsOn(startScripts, initScript, checkScript, yourkitAgent, yourkitLicense);
            task.dependsOn(copyLauncherBinaries, launchConfigTask, manifest, manifestClassPathTask);
        });

        project.afterEvaluate(p -> DistTarTask.configure(
                distTar.get(),
                project,
                distributionExtension.getDistributionServiceName().get(),
                distributionExtension.getExcludeFromVar().get(),
                distributionExtension.getEnableManifestClasspath().get()));

        project.getArtifacts().add(SlsBaseDistPlugin.SLS_CONFIGURATION_NAME, distTar);
    }
}
