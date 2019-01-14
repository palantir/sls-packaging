/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.gradle.dist.ProductType
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

@CompileStatic
class ConfigTarTask {
    private static final ObjectMapper OBJECT_WRITER = new ObjectMapper();
    private static final String PRODUCT_TYPE_REGEX = "^\\w+?\\.v\\d+\$"

    static Tar createConfigTarTask(Project project, String taskName, ProductType productType) {
        project.tasks.<Tar>create(taskName, Tar) { p ->
            p.group = JavaServiceDistributionPlugin.GROUP_NAME
            p.description = "Creates a compressed, gzipped tar file that contains the sls configuration files for the product"
            // Set compression in constructor so that task output has the right name from the start.
            p.compression = Compression.GZIP

            def productTypeString = OBJECT_WRITER.writeValueAsString(productType)
            p.extension = productTypeString.substring(0, productTypeString.lastIndexOf('.')).concat(".config.tgz")
        }
    }

    @CompileDynamic
    static void configure(Tar distTar, Project project, String serviceName) {
        distTar.configure {
            setBaseName(serviceName)
            // do the things that the java plugin would otherwise do for us
            def version = String.valueOf(project.version)
            setVersion(version)
            setDestinationDir(new File("${project.buildDir}/distributions"))
            String archiveRootDir = serviceName + '-' + version

            from("${project.projectDir}/deployment") {
                into "${archiveRootDir}/deployment"
            }

            from("${project.buildDir}/deployment") {
                into "${archiveRootDir}/deployment"
            }
        }
    }
}
