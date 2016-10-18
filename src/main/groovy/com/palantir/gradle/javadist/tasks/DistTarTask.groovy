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
package com.palantir.gradle.javadist.tasks

import com.palantir.gradle.javadist.DistributionExtension
import com.palantir.gradle.javadist.JavaDistributionPlugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

class DistTarTask {

    public static Tar createDistTarTask (Project project, String taskName) {
        def distTar = project.tasks.create(taskName, Tar.class)
        distTar.group = JavaDistributionPlugin.GROUP_NAME
        distTar.description = "Creates a compressed, gzipped tar file that contains required runtime resources."
        // Set compression in constructor so that task output has the right name from the start.
        distTar.compression = Compression.GZIP
        distTar.extension = 'sls.tgz'

        // Doing Tar task configuration in project.afterEvaluate to wait for proper configuration
        // of project.distributionExtension() - DO NOT FIDDLE WITH RUNTIME CONFIGURATIONS IN THIS
        // CLOSURE AS IT WILL FORCE DEPENDENCY RESOLUTION DURING CONFIGURATION TIME AND THAT IS
        // BAD BECAUSE IT SLOWS DOWN ALL THE OTHER TASKS.
        project.afterEvaluate {
            distTar.baseName = project.distributionExtension().serviceName
            String archiveRootDir = project.distributionExtension().serviceName + '-' + String.valueOf(project.version)
            distTar.from("${project.projectDir}/var") {
                into "${archiveRootDir}/var"

                project.distributionExtension().excludeFromVar.each {
                    exclude it
                }
            }

            new File(project.buildDir, "gjd-tmp/var/data/tmp").mkdirs()
            distTar.from ("${project.buildDir}/gjd-tmp/var/data") {
                into "${archiveRootDir}/var/data"
            }

            distTar.from("${project.projectDir}/deployment") {
                into "${archiveRootDir}/deployment"
            }

            distTar.from("${project.projectDir}/service") {
                into "${archiveRootDir}/service"
            }

            distTar.into("${archiveRootDir}/service/lib") {
                from(project.tasks.jar.outputs.files)
                from(project.configurations.runtime)
            }

            if (project.distributionExtension().isEnableManifestClasspath()) {
                distTar.into("${archiveRootDir}/service/lib") {
                    from(project.tasks.getByName("manifestClasspathJar"))
                }
            }

            distTar.into("${archiveRootDir}/service/bin") {
                from("${project.buildDir}/scripts")
                fileMode = 0755
            }

            distTar.into("${archiveRootDir}/service/monitoring/bin") {
                from("${project.buildDir}/monitoring")
                fileMode = 0755
            }

            distTar.into("${archiveRootDir}/deployment") {
                from("${project.buildDir}/deployment")
            }
        }

        return distTar
    }
}
