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

class JavaDistributionPlugin implements Plugin<Project> {

    static final String GROUP_NAME = "Distribution"
    static final String SLS_CONFIGURATION_NAME = "sls"

    void apply(Project project) {
        project.plugins.apply('java')
        project.extensions.create('distribution', DistributionExtension)

        project.configurations.create('goJavaLauncherBinaries')
        project.dependencies {
            goJavaLauncherBinaries 'com.palantir.launching:go-java-launcher:1.0.1'
        }

        project.ext.set ("distributionExtension", {
            return project.extensions.findByType(DistributionExtension)
        })

        // Create tasks
        Task manifestClasspathJar = ManifestClasspathJarTask.createManifestClasspathJarTask(project, 'manifestClasspathJar')
        Task startScripts = project.tasks.create('createStartScripts', CreateStartScriptsTask)
        Task copyLauncherBinaries = CopyLauncherBinariesTask.createCopyLauncherBinariesTask(project, 'copyLauncherBinaries')
        Task launchConfig = project.tasks.create('createLaunchConfig', LaunchConfigTask)
        Task initScript = project.tasks.create('createInitScript', CreateInitScriptTask)
        Task checkScript = project.tasks.create('createCheckScript', CreateCheckScriptTask)
        Task manifest = project.tasks.create('createManifest', CreateManifestTask)
        Task distTar = DistTarTask.createDistTarTask(project, 'distTar')
        Task run = RunTask.createRunTask(project, 'run')

        // Create configuration and exported artifacts
        project.configurations.create(SLS_CONFIGURATION_NAME)
        project.artifacts.add(SLS_CONFIGURATION_NAME, distTar)

        // Configure tasks
        distTar.dependsOn startScripts, initScript, checkScript, copyLauncherBinaries, launchConfig, manifest, manifestClasspathJar
    }
}
