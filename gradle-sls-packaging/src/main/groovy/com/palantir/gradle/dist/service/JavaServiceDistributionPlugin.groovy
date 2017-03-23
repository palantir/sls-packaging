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

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Tar

import com.palantir.gradle.dist.asset.AssetDistributionPlugin
import com.palantir.gradle.dist.service.tasks.*
import com.palantir.gradle.dist.tasks.CreateManifestTask

class JavaServiceDistributionPlugin implements Plugin<Project> {

    static final String GROUP_NAME = "Distribution"
    static final String SLS_CONFIGURATION_NAME = "sls"

    void apply(Project project) {
        if (project.getPlugins().hasPlugin(AssetDistributionPlugin)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-asset-distribution' and 'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.")
        }
        project.plugins.apply('java')

        project.configurations.create('goJavaLauncherBinaries')
        project.dependencies {
            goJavaLauncherBinaries 'com.palantir.launching:go-java-launcher:1.1.1'
        }
        CopyLauncherBinariesTask copyLauncherBinaries = project.tasks.create('copyLauncherBinaries', CopyLauncherBinariesTask)

        JavaServiceDistributionExtension ext = project.extensions.create('distribution', JavaServiceDistributionExtension, project)

        Jar manifestClasspathJar = createManifestClasspathJarTask(project, ext)
        CreateStartScripts startScripts = createCreateStartScriptsTask(project, manifestClasspathJar, ext)
        LaunchConfigTask launchConfig = createLaunchConfigTask(project, ext)

        CreateInitScriptTask initScript = project.tasks.create('createInitScript', CreateInitScriptTask)
        project.afterEvaluate {
            initScript.configure(ext.serviceName)
        }

        CreateCheckScriptTask checkScript = project.tasks.create('createCheckScript', CreateCheckScriptTask) {
            group JavaServiceDistributionPlugin.GROUP_NAME
            description "Generates healthcheck (service/monitoring/bin/check.sh) script."

            serviceName { ext.serviceName }
            checkArgs { ext.checkArgs }
        }

        CreateManifestTask manifest = project.tasks.create('createManifest', CreateManifestTask)
        project.afterEvaluate {
            manifest.configure(
                    ext.serviceName,
                    ext.serviceGroup,
                    ext.productType,
                    ext.manifestExtensions,
                    ext.serviceDependencies
            )
        }

        Tar distTar = DistTarTask.createDistTarTask(project, 'distTar')
        project.afterEvaluate {
            DistTarTask.configure(
                    distTar,
                    project,
                    ext.serviceName,
                    ext.excludeFromVar,
                    ext.isEnableManifestClasspath())
        }

        project.tasks.create('run', RunTask) {
            group JavaServiceDistributionPlugin.GROUP_NAME
            description "Runs the specified project using configured mainClass and with default args."

            mainClass { ext.mainClass }
            args { ext.args }
            defaultJvmOpts { ext.defaultJvmOpts }
        }

        // Create configuration and exported artifacts
        project.configurations.create(SLS_CONFIGURATION_NAME)
        project.artifacts.add(SLS_CONFIGURATION_NAME, distTar)

        // Configure tasks
        distTar.dependsOn startScripts, initScript, checkScript, copyLauncherBinaries, launchConfig, manifest, manifestClasspathJar
    }

    private Jar createManifestClasspathJarTask(Project project, JavaServiceDistributionExtension ext) {
        Jar manifestClasspathJar = project.tasks.create('manifestClasspathJar', Jar) {
            group JavaServiceDistributionPlugin.GROUP_NAME
            description 'Creates a jar containing a Class-Path manifest entry specifying the classpath using pathing ' +
                    'jar rather than command line argument on Windows, since Windows path sizes are limited.'
            appendix 'manifest-classpath'
            doFirst {
                manifest.attributes "Class-Path": project.files(project.configurations.runtime)
                        .collect { it.getName() }.join(' ') + ' ' + archiveName
            }
        }
        manifestClasspathJar.onlyIf { ext.isEnableManifestClasspath() }
        return manifestClasspathJar
    }

    private CreateStartScripts createCreateStartScriptsTask(Project project, Jar manifestClasspathJar, JavaServiceDistributionExtension ext) {
        CreateStartScripts startScripts =  project.tasks.create('createStartScripts', CreateStartScripts) {
            group JavaServiceDistributionPlugin.GROUP_NAME

            setOutputDir project.file("${project.buildDir}/scripts")
            setClasspath project.tasks['jar'].outputs.files + project.configurations.runtime

            doLast {
                if (ext.enableManifestClasspath) {
                    // Replace standard classpath with pathing jar in order to circumnavigate length limits:
                    // https://issues.gradle.org/browse/GRADLE-2992
                    def winScriptFile = project.file getWindowsScript()
                    def winFileText = winScriptFile.text

                    // Remove too-long-classpath and use pathing jar instead
                    winFileText = winFileText.replaceAll('set CLASSPATH=.*', 'rem CLASSPATH declaration removed.')
                    winFileText = winFileText.replaceAll(
                        '("%JAVA_EXE%" .* -classpath ")%CLASSPATH%(" .*)',
                        '$1%APP_HOME%\\\\lib\\\\' + manifestClasspathJar.archiveName + '$2')

                    winScriptFile.text = winFileText
                }
            }
        }
        startScripts.conventionMapping.map('mainClassName', { ext.mainClass })
        startScripts.conventionMapping.map('applicationName', { ext.serviceName })
        startScripts.conventionMapping.map('defaultJvmOpts', { ext.defaultJvmOpts })
        return startScripts
    }

    private LaunchConfigTask createLaunchConfigTask(Project project, JavaServiceDistributionExtension ext) {
        LaunchConfigTask launchConfig = project.tasks.create('createLaunchConfig', LaunchConfigTask) {
            group JavaServiceDistributionPlugin.GROUP_NAME
            description 'Generates launcher-static.yml and launcher-check.yml configurations.'

            setClasspath project.tasks[JavaPlugin.JAR_TASK_NAME].outputs.files + project.configurations.runtime
        }
        launchConfig.conventionMapping.map('mainClass', { ext.mainClass })
        launchConfig.conventionMapping.map('args', { ext.args })
        launchConfig.conventionMapping.map('checkArgs', { ext.checkArgs })
        launchConfig.conventionMapping.map('defaultJvmOpts', { ext.defaultJvmOpts })
        launchConfig.conventionMapping.map('javaHome', { ext.javaHome })
        launchConfig.conventionMapping.map('env', { ext.env })
        return launchConfig
    }
}
