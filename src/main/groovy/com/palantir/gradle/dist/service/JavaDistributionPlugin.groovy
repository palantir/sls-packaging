/*
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.dist.service

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.gradle.dist.asset.AssetDistributionPlugin
import com.palantir.gradle.dist.service.tasks.*
import com.palantir.gradle.dist.tasks.CreateManifestTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Tar
import org.gradle.jvm.application.tasks.CreateStartScripts

class JavaDistributionPlugin implements Plugin<Project> {

    static final String GROUP_NAME = "Distribution"
    static final String SLS_CONFIGURATION_NAME = "sls"

    void apply(Project project) {
        if (project.getPlugins().hasPlugin(AssetDistributionPlugin)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.asset-distribution' and 'com.palantir.java-distribution' cannot be used in the same Gradle project.")
        }
        project.plugins.apply('java')
        project.extensions.create('distribution', ServiceDistributionExtension, project)

        project.configurations.create('goJavaLauncherBinaries')
        project.dependencies {
            goJavaLauncherBinaries 'com.palantir.launching:go-java-launcher:1.1.1'
        }

        def distributionExtension = project.extensions.findByType(ServiceDistributionExtension)

        // Create tasks
        Task manifestClasspathJar = ManifestClasspathJarTask.createManifestClasspathJarTask(project, "manifestClasspathJar")
        project.afterEvaluate {
            manifestClasspathJar.onlyIf { distributionExtension.isEnableManifestClasspath() }
        }

        CreateStartScripts startScripts = CreateStartScriptsTask.createStartScriptsTask(project, 'createStartScripts')
        project.afterEvaluate {
            CreateStartScriptsTask.configure(startScripts, distributionExtension.mainClass, distributionExtension.serviceName,
                    distributionExtension.defaultJvmOpts, distributionExtension.enableManifestClasspath)
        }

        CopyLauncherBinariesTask copyLauncherBinaries = project.tasks.create('copyLauncherBinaries', CopyLauncherBinariesTask)

        LaunchConfigTask launchConfig = project.tasks.create('createLaunchConfig', LaunchConfigTask)
        project.afterEvaluate {
            launchConfig.configure(distributionExtension.mainClass, distributionExtension.args, distributionExtension.checkArgs,
                    distributionExtension.defaultJvmOpts, distributionExtension.javaHome, distributionExtension.env,
                    project.tasks[JavaPlugin.JAR_TASK_NAME].outputs.files + project.configurations.runtime)
        }

        CreateInitScriptTask initScript = project.tasks.create('createInitScript', CreateInitScriptTask)
        project.afterEvaluate {
            initScript.configure(distributionExtension.serviceName)
        }

        CreateCheckScriptTask checkScript = project.tasks.create('createCheckScript', CreateCheckScriptTask)
        project.afterEvaluate {
            checkScript.configure(distributionExtension.serviceName, distributionExtension.checkArgs)
        }

        CreateManifestTask manifest = project.tasks.create('createManifest', CreateManifestTask)
        project.afterEvaluate {
            // Serialize service-dependencies, add them to manifestExtensions
            def mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
            def dependencies = []
            distributionExtension.serviceDependencies.each {
                dependencies.add(mapper.convertValue(it, Map))
            }
            if (distributionExtension.manifestExtensions.containsKey("service-dependencies")) {
                throw new IllegalArgumentException("Use serviceDependencies configuration option instead of setting " +
                        "'service-dependencies' key in manifestExtensions")
            }
            distributionExtension.manifestExtensions.put("service-dependencies", dependencies)

            manifest.configure(
                    distributionExtension.serviceName,
                    distributionExtension.serviceGroup,
                    distributionExtension.productType,
                    distributionExtension.manifestExtensions,
            )
        }

        Tar distTar = DistTarTask.createDistTarTask(project, 'distTar')
        project.afterEvaluate {
            DistTarTask.configure(distTar, distributionExtension.serviceName, distributionExtension.excludeFromVar, distributionExtension.isEnableManifestClasspath())
        }

        JavaExec run = RunTask.createRunTask(project, 'run')
        project.afterEvaluate {
            RunTask.configure(run, distributionExtension.mainClass, distributionExtension.args, distributionExtension.defaultJvmOpts,)
        }

        // Create configuration and exported artifacts
        project.configurations.create(SLS_CONFIGURATION_NAME)
        project.artifacts.add(SLS_CONFIGURATION_NAME, distTar)

        // Configure tasks
        distTar.dependsOn startScripts, initScript, checkScript, copyLauncherBinaries, launchConfig, manifest, manifestClasspathJar
    }
}
