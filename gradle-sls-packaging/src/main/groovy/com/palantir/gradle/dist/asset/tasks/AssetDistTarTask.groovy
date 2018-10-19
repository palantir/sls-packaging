/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.dist.asset.tasks

import com.palantir.gradle.dist.asset.AssetDistributionPlugin
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

@CompileStatic
class AssetDistTarTask {

    static Tar createAssetDistTarTask(Project project, String taskName) {
        return project.tasks.<Tar>create(taskName, Tar) { p ->
            p.group = AssetDistributionPlugin.GROUP_NAME
            p.description = "Creates a compressed, gzipped tar file that contains required static assets."
            // Set compression in constructor so that task output has the right name from the start.
            p.compression = Compression.GZIP
            p.extension = 'sls.tgz'
        }
    }

    static void configure(Tar distTar, String serviceName, Map<String, String> assetDirs) {
        distTar.with {
            setBaseName(serviceName)
            // do the things that the java plugin would otherwise do for us
            def version = String.valueOf(project.version)
            setVersion(version)
            setDestinationDir(new File("${project.buildDir}/distributions"))
            String archiveRootDir = serviceName + '-' + version

            from("${project.projectDir}/deployment") {
                into "${archiveRootDir}/deployment"
            }

            into("${archiveRootDir}/deployment") {
                from("${project.buildDir}/deployment")
            }

            assetDirs.entrySet().each { entry ->
                from(project.file(entry.getKey())) {
                    into("${archiveRootDir}/asset/${entry.getValue()}")
                }
            }
        }
    }
}
