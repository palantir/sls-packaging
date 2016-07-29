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
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

class DistTarTask extends Tar {

    DistTarTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Creates a compressed, gzipped tar file that contains required runtime resources."
        // Set compression in constructor so that task output has the right name from the start.
        compression = Compression.GZIP

        project.afterEvaluate {
            String archiveRootDir = distributionExtension().serviceName + '-' + String.valueOf(project.version)

            from("${project.projectDir}/var") {
                into "${archiveRootDir}/var"

                exclude 'log'
                exclude 'run'

                distributionExtension().excludeFromVar.each {
                    exclude it
                }
            }

            new File(project.projectDir, "var/data/tmp").mkdirs()

            from("${project.projectDir}/deployment") {
                into "${archiveRootDir}/deployment"
            }

            from("${project.projectDir}/service") {
                into "${archiveRootDir}/service"
            }

            into("${archiveRootDir}/service/lib") {
                from(project.tasks.jar.outputs.files)
                from(project.configurations.runtime)
            }

            if (distributionExtension().isEnableManifestClasspath()) {
                into("${archiveRootDir}/service/lib") {
                    from(project.tasks.getByName("manifestClasspathJar"))
                }
            }

            into("${archiveRootDir}/service/bin") {
                from("${project.buildDir}/scripts")
                fileMode = 0755
            }

            into("${archiveRootDir}/service/monitoring/bin") {
                from("${project.buildDir}/monitoring")
                fileMode = 0755
            }

            into("${archiveRootDir}/deployment") {
                from("${project.buildDir}/deployment")
            }
        }
    }

    DistributionExtension distributionExtension() {
        return project.extensions.findByType(DistributionExtension)
    }

    @Override
    public String getBaseName() {
        // works around a bug where something in the tar task hierarchy either resolves the wrong
        // getBaseName() call or uses baseName directly.
        setBaseName(distributionExtension().serviceName)
        return super.getBaseName()
    }
}
