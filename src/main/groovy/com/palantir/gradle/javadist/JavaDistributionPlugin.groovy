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
import org.gradle.util.GUtil

import java.nio.file.Paths

class JavaDistributionPlugin implements Plugin<Project> {

    void apply(Project project) {
        // force application of java
        project.plugins.apply('java')

        DistributionExtension ext = project.extensions.create('distribution', DistributionExtension)

        // Specify classpath using pathing jar rather than command line argument on Windows, since
        // Windows path sizes are limited.
        ManifestClasspathJarTask manifestClasspathJar =
            project.tasks.create("manifestClasspathJar", ManifestClasspathJarTask)
        manifestClasspathJar.setOnlyIf {
            ext.isEnableManifestClasspath()
        }

        Task startScripts = project.tasks.create('createStartScripts', DistributionCreateStartScriptsTask, {
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
            description = "Generates daemonizing init.sh script."
        }) << {
            EmitFiles.replaceVars(
                    JavaDistributionPlugin.class.getResourceAsStream('/wrapper.conf'),
                    Paths.get("${project.buildDir}/" + ext.wrapperConfPath),
                    ['@applicationNameOpts@': GUtil.toConstant(ext.serviceName)+ '_OPTS'])
            .toFile()

            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/init.sh'),
                Paths.get("${project.buildDir}/scripts/init.sh"),
                ['@serviceName@': ext.serviceName,
                 '@args@':  ext.args.iterator().join(' '),
                 '@wrapperConfigPath@': ext.wrapperConfPath ])
            .toFile()
            .setExecutable(true)
        }

        Task manifest = project.tasks.create('createManifest', {
            description = "Generates a simple yaml file describing the package content."
        }) << {
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/manifest.yaml'),
                Paths.get("${project.buildDir}/deployment/manifest.yaml"),
                ['@serviceName@': ext.serviceName,
                 '@serviceVersion@': String.valueOf(project.version)])
            .toFile()
            .setExecutable(true)
        }

        DistTarTask distTar = project.tasks.create('distTar', DistTarTask, {
            description = "Creates a compressed, gzipped tar file that contains required runtime resources."
            dependsOn startScripts, initScript, manifest, manifestClasspathJar
        })

        RunTask run = project.tasks.create('run', RunTask, {
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
