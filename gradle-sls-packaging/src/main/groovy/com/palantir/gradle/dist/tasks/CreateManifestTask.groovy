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


import com.fasterxml.jackson.core.type.TypeReference
import com.palantir.gradle.dist.DependenciesUtil
import com.palantir.gradle.dist.ProductDependency
import com.palantir.gradle.dist.ProductId
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import com.palantir.slspackaging.versions.SlsProductVersions
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class CreateManifestTask extends DefaultTask {

    public static String SLS_RECOMMENDED_PRODUCT_DEPS_KEY = "Sls-Recommended-Product-Dependencies"

    CreateManifestTask() {
        group = JavaServiceDistributionPlugin.GROUP_NAME
        description = "Generates a simple yaml file describing the package content."
    }

    @Input
    String serviceName

    @Input
    String serviceGroup

    @Input
    String productType

    @Input
    Set<ProductDependency> productDependencies

    @Input
    Map<String, Object> manifestExtensions

    @Input
    Configuration productDependenciesConfig

    Set<ProductId> ignoredProductIds

    @Input
    String getProjectVersion() {
        def stringVersion = String.valueOf(project.version)
        if (!SlsProductVersions.isValidVersion(stringVersion)) {
            throw new IllegalArgumentException("Project version must be a valid SLS version: " + stringVersion)
        }

        if (!SlsProductVersions.isOrderableVersion(stringVersion)) {
            project.logger.warn("Version string in project {} is not orderable as per SLS specification: {}",
                    project.name, stringVersion)
        }
        return stringVersion
    }

    @OutputFile
    File getManifestFile() {
        return new File("${project.buildDir}/deployment/manifest.yml")
    }

    @TaskAction
    void createManifest() {
        // Make sure we ignore this product
        def newIgnoredProductIds = ignoredProductIds + new ProductId(serviceGroup, serviceName)

        def dependencies = DependenciesUtil
                .resolveDependencies(project, productDependenciesConfig, productDependencies, newIgnoredProductIds)
                .collect { DependenciesUtil.jsonMapper.convertValue(it, new TypeReference<Map<String, Object>>() {}) }

        manifestExtensions.put("product-dependencies", dependencies)
        getManifestFile().setText(JsonOutput.prettyPrint(JsonOutput.toJson([
                'manifest-version': '1.0',
                'product-type'    : productType,
                'product-group'   : serviceGroup,
                'product-name'    : serviceName,
                'product-version' : projectVersion,
                'extensions'      : manifestExtensions,
        ])))
    }


    void configure(
            String serviceName,
            String serviceGroup,
            String productType,
            Map<String, Object> manifestExtensions,
            List<ProductDependency> productDependencies,
            Configuration productDependenciesConfig,
            Set<ProductId> ignoredProductIds) {
        // Serialize service-dependencies, add them to manifestExtensions
        if (manifestExtensions.containsKey("product-dependencies")) {
            throw new IllegalArgumentException("Use productDependencies configuration option instead of setting " +
                    "'service-dependencies' key in manifestExtensions")
        }
        this.serviceName = serviceName
        this.serviceGroup = serviceGroup
        this.productType = productType
        this.productDependencies = productDependencies
        this.manifestExtensions = manifestExtensions
        this.productDependenciesConfig = productDependenciesConfig
        this.ignoredProductIds = ignoredProductIds
        dependsOn(productDependenciesConfig)
    }

    static String identifierToCoord(ModuleVersionIdentifier identifier) {
        return "${identifier.group}:${identifier.name}:${identifier.version}".toString()
    }

}
