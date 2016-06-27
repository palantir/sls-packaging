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

import java.nio.file.Paths

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

class JavaDistributionPlugin implements Plugin<Project> {

    private static final String GROUP_NAME = "Distribution"

    void apply(Project project) {
        // force application of java
        project.plugins.apply('java')

        DistributionExtension ext = project.extensions.create('distribution', DistributionExtension)

        // Specify classpath using pathing jar rather than command line argument on Windows, since
        // Windows path sizes are limited.
        ManifestClasspathJarTask manifestClasspathJar = project.tasks.create("manifestClasspathJar", ManifestClasspathJarTask, {
            group = GROUP_NAME
            onlyIf { ext.isEnableManifestClasspath() }
        })

        Task startScripts = project.tasks.create('createStartScripts', DistributionCreateStartScriptsTask, {
            group = GROUP_NAME
            description = "Generates standard Java start scripts."
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

        Task initScript = project.tasks.create('createInitScript', {
            group = GROUP_NAME
            description = "Generates daemonizing init.sh script."
        }) << {
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/init.sh'),
                Paths.get("${project.buildDir}/scripts/init.sh"),
                ['@serviceName@': ext.serviceName,
                 '@args@': ext.args.iterator().join(' ')])
            .toFile()
            .setExecutable(true)
        }

        Task checkScript = project.tasks.create('createCheckScript', {
            group = GROUP_NAME
            description = "Generates healthcheck (service/monitoring/bin/check.sh) script."
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

        Task configScript = project.tasks.create('createConfigScript', {
            group = GROUP_NAME
            description = "Generates config.sh script."
        }) << {
            String javaHome = ext.javaHome != null ? 'JAVA_HOME="' + ext.javaHome + '"' : '#JAVA_HOME=""'
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/config.sh'),
                Paths.get("${project.buildDir}/scripts/config.sh"),
                ['@javaHome@': javaHome])
        }

        Task manifest = project.tasks.create('createManifest', {
            group = GROUP_NAME
            description = "Generates a simple yaml file describing the package content."
        }) << {
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/manifest.yml'),
                Paths.get("${project.buildDir}/deployment/manifest.yml"),
                ['@serviceName@': ext.serviceName,
                 '@serviceVersion@': String.valueOf(project.version)])
            .toFile()
        }

        DistTarTask distTar = project.tasks.create('distTar', DistTarTask, {
            group = GROUP_NAME
            description = "Creates a compressed, gzipped tar file that contains required runtime resources."
            dependsOn startScripts, initScript, checkScript, configScript, manifest, manifestClasspathJar
            distributionExtension ext
        })

        RunTask run = project.tasks.create('run', RunTask, {
            group = GROUP_NAME
            description = "Runs the specified project using configured mainClass and with default args."
        })

        project.afterEvaluate {
            manifestClasspathJar.configure(ext)
            startScripts.configure(ext)
            distTar.configure(ext)
            run.configure(ext)
        }
    }

}
