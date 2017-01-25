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

import com.palantir.gradle.dist.SlsProductVersions
import com.palantir.gradle.dist.service.JavaDistributionPlugin
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class CreateManifestTask extends DefaultTask {

    @Input
    String serviceName

    @Input
    String serviceGroup

    @Input
    String productType

    @Input
    Map<String, Object> manifestExtensions

    CreateManifestTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Generates a simple yaml file describing the package content."
    }

    @Input
    public String getProjectVersion() {
        def stringVersion = String.valueOf(project.version)
        if (!SlsProductVersions.isValidVersion(stringVersion)) {
            throw new IllegalArgumentException("Project version must be a valid SLS version: " + stringVersion)
        }
        return stringVersion
    }

    @OutputFile
    File getManifestFile() {
        return new File("${project.buildDir}/deployment/manifest.yml")
    }

    @TaskAction
    void createManifest() {
        getManifestFile().setText(JsonOutput.prettyPrint(JsonOutput.toJson([
                'manifest-version': '1.0',
                'product-type': productType,
                'product-group': serviceGroup,
                'product-name': serviceName,
                'product-version': projectVersion,
                'extensions': manifestExtensions,
        ])))
    }

    public void configure(
            String serviceName, String serviceGroup, String productType, Map<String, Object> manifestExtensions) {
        this.serviceName = serviceName
        this.serviceGroup = serviceGroup
        this.productType = productType
        this.manifestExtensions = manifestExtensions
    }
}
