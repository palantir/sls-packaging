/*
 * Copyright 2015 Palantir Technologies
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Copy

import java.nio.file.Paths

class JavaDistributionPlugin implements Plugin<Project> {

    private static final String GROUP_NAME = "Distribution"
    private static final String SLS_CONFIGURATION_NAME = "sls"

    void apply(Project project) {
        // force application of java
        project.plugins.apply('java')

        project.configurations.create('goJavaLauncherBinaries')
        project.dependencies {
            goJavaLauncherBinaries 'com.palantir.launching:go-java-launcher:1.0.1'
        }

        DistributionExtension ext = project.extensions.create('distribution', DistributionExtension)

        // Specify classpath using pathing jar rather than command line argument on Windows, since
        // Windows path sizes are limited.
        ManifestClasspathJarTask manifestClasspathJar = project.tasks.create("manifestClasspathJar", ManifestClasspathJarTask, {
            it.group = GROUP_NAME
            it.onlyIf { ext.isEnableManifestClasspath() }
        })

        Task startScripts = project.tasks.create('createStartScripts', DistributionCreateStartScriptsTask, {
            it.group = GROUP_NAME
            it.description = "Generates standard Java start scripts."
        }) << {
            if (ext.isEnableManifestClasspath()) {
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

        Task copyLauncherBinaries = project.tasks.create('copyLauncherBinaries', Copy, {
            it.group = GROUP_NAME
            it.description = "Creates go-java-launcher binaries."
        })
        project.afterEvaluate {
            copyLauncherBinaries.configure {
                def zipPath = project.configurations.goJavaLauncherBinaries.find {
                    it.name.startsWith("go-java-launcher")
                }
                def zipFile = project.file(zipPath)

                it.from project.tarTree(zipFile)
                it.into "${project.buildDir}/scripts"
                it.includeEmptyDirs = false

                // remove first three levels of directory structure from Tar container
                it.eachFile { FileCopyDetails fcp ->
                    fcp.relativePath = new RelativePath(
                            !fcp.file.isDirectory(),
                            fcp.relativePath.segments[3..-1] as String[])
                }
            }
        }

        LaunchConfigTask launchConfig = project.tasks.create('createLaunchConfig', LaunchConfigTask, {
            it.group = GROUP_NAME
            it.description = "Generates launcher-static.yml and launcher-check.yml configurations."
        })

        Task initScript = project.tasks.create('createInitScript', {
            it.group = GROUP_NAME
            it.description = "Generates daemonizing init.sh script."
        }) << {
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/init.sh'),
                Paths.get("${project.buildDir}/scripts/init.sh"),
                ['@serviceName@': ext.serviceName])
            .toFile()
            .setExecutable(true)
        }

        Task checkScript = project.tasks.create('createCheckScript', {
            it.group = GROUP_NAME
            it.description = "Generates healthcheck (service/monitoring/bin/check.sh) script."
        }) << {
            if (!ext.checkArgs.empty) {
                EmitFiles.replaceVars(
                    JavaDistributionPlugin.class.getResourceAsStream('/check.sh'),
                    Paths.get("${project.buildDir}/monitoring/check.sh"),
                    ['@serviceName@': ext.serviceName,
                     '@checkArgs@': ext.checkArgs.iterator().join(' ')])
                .toFile()
                .setExecutable(true)
            }
        }

        Task manifest = project.tasks.create('createManifest', {
            it.group = GROUP_NAME
            it.description = "Generates a simple yaml file describing the package content."
        }) << {
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/manifest.yml'),
                Paths.get("${project.buildDir}/deployment/manifest.yml"),
                ['@serviceName@': ext.serviceName,
                 '@serviceVersion@': String.valueOf(project.version)])
            .toFile()
        }

        DistTarTask distTar = project.tasks.create('distTar', DistTarTask, {
            it.group = GROUP_NAME
            it.description = "Creates a compressed, gzipped tar file that contains required runtime resources."
            it.dependsOn startScripts, initScript, checkScript, copyLauncherBinaries, launchConfig, manifest,
                    manifestClasspathJar
            it.distributionExtension ext
        })

        RunTask run = project.tasks.create('run', RunTask, {
            it.group = GROUP_NAME
            it.description = "Runs the specified project using configured mainClass and with default args."
        })

        project.configurations.create(SLS_CONFIGURATION_NAME)
        project.artifacts.add(SLS_CONFIGURATION_NAME, distTar)

        project.afterEvaluate {
            manifestClasspathJar.configure(ext)
            startScripts.configure(ext)
            launchConfig.configure(ext)
            distTar.configure(ext)
            run.configure(ext)
        }
    }
}
