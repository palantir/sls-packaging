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
package com.palantir.gradle.javadist

import com.palantir.gradle.javadist.tasks.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin

class JavaDistributionPlugin implements Plugin<Project> {

    static final String GROUP_NAME = "Distribution"
    static final String SLS_CONFIGURATION_NAME = "sls"

    void apply(Project project) {
        project.plugins.apply('java')
        project.extensions.create('distribution', DistributionExtension, project)

        project.configurations.create('goJavaLauncherBinaries')
        project.dependencies {
            goJavaLauncherBinaries 'com.palantir.launching:go-java-launcher:1.1.1'
        }

        def distributionExtension = project.extensions.findByType(DistributionExtension)

        // Create tasks
        Task manifestClasspathJar = ManifestClasspathJarTask.createManifestClasspathJarTask(project, "manifestClasspathJar")
        project.afterEvaluate {
            manifestClasspathJar.onlyIf { distributionExtension.isEnableManifestClasspath() }
        }

        Task startScripts = CreateStartScriptsTask.createStartScriptsTask(project, 'createStartScripts')
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

        Task initScript = project.tasks.create('createInitScript', CreateInitScriptTask)
        project.afterEvaluate {
            initScript.configure(distributionExtension.serviceName)
        }

        Task checkScript = project.tasks.create('createCheckScript', CreateCheckScriptTask)
        project.afterEvaluate {
            checkScript.configure(distributionExtension.serviceName, distributionExtension.checkArgs)
        }

        Task manifest = project.tasks.create('createManifest', CreateManifestTask)
        project.afterEvaluate {
            manifest.configure(
                    distributionExtension.serviceName,
                    distributionExtension.serviceGroup,
                    distributionExtension.manifestExtensions,
            )
        }

        Task distTar = DistTarTask.createDistTarTask(project, 'distTar')
        project.afterEvaluate {
            DistTarTask.configure(distTar, distributionExtension.serviceName, distributionExtension.excludeFromVar, distributionExtension.isEnableManifestClasspath())
        }

        Task run = RunTask.createRunTask(project, 'run')
        project.afterEvaluate {
            RunTask.configure(run, distributionExtension.mainClass, distributionExtension.args, distributionExtension.defaultJvmOpts, )
        }


        // Create configuration and exported artifacts
        project.configurations.create(SLS_CONFIGURATION_NAME)
        project.artifacts.add(SLS_CONFIGURATION_NAME, distTar)

        // Configure tasks
        distTar.dependsOn startScripts, initScript, checkScript, copyLauncherBinaries, launchConfig, manifest, manifestClasspathJar
    }
}
