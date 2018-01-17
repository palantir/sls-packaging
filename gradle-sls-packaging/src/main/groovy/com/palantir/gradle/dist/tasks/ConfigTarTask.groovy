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

package com.palantir.gradle.dist.tasks

import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar

class ConfigTarTask {
    private static final String PRODUCT_TYPE_REGEX = "^\\w+?\\.v\\d+\$"

    static Tar createConfigTarTask(Project project, String taskName, String productType) {
        project.tasks.create(taskName, Tar) { p ->
            p.group = JavaServiceDistributionPlugin.GROUP_NAME
            p.description = "Creates a compressed, gzipped tar file that contains the sls configuration files for the product"
            // Set compression in constructor so that task output has the right name from the start.
            p.compression = Compression.GZIP
            // The extension is the product type without the version
            // service.v1 -> .service.config.tgz
            if (!productType.matches(PRODUCT_TYPE_REGEX)) {
                throw new IllegalArgumentException(String.format("Product type must end with '.v<VERSION_NUMBER>': %s", productType))
            }
            p.extension = productType.substring(0, productType.lastIndexOf('.')).concat(".config.tgz")
        }
    }

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
