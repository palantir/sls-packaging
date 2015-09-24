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

import com.google.common.collect.ImmutableMap

class JavaDistributionPlugin implements Plugin<Project> {

    void apply(Project project) {
        // force application of java
        project.plugins.apply('java')

        DistributionExtension ext = project.extensions.create('distribution', DistributionExtension)

        Task startScripts = project.tasks.create('createStartScripts', DistributionCreateStartScriptsTask, {
            description = "Generates standard Java start scripts."
        })

        Task initScript = project.tasks.create('createInitScript', {
            description = "Generates daemonizing init.sh script."
        }) << {
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/init.sh'),
                Paths.get("${project.buildDir}/scripts/init.sh"),
                ImmutableMap.of(
                    '@serviceName@', ext.serviceName,
                    '@args@',  ext.args.iterator().join(' ')))
            .toFile()
            .setExecutable(true)
        }

        Task manifest = project.tasks.create('createManifest', {
            description = "Generates a simple yaml file describing the package content."
        }) << {
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/manifest.yaml'),
                Paths.get("${project.buildDir}/deployment/manifest.yaml"),
                ImmutableMap.of(
                    '@serviceName@', ext.serviceName,
                    '@serviceVersion@',  project.version))
            .toFile()
            .setExecutable(true)
        }

        DistTarTask distTar = project.tasks.create('distTar', DistTarTask, {
            description = "Creates a compressed, gzipped tar file that contains required runtime resources."
            dependsOn startScripts, initScript, manifest
        })

        RunTask run = project.tasks.create('run', RunTask, {
            description = "Runs the specified project using configured mainClass and with default args."
        })

        project.afterEvaluate {
            startScripts.configure(ext)
            distTar.configure(ext)
            run.configure(ext)
        }
    }

}
