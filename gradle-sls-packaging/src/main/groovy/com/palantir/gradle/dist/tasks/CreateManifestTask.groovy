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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.palantir.gradle.dist.ProductDependency
import com.palantir.gradle.dist.ProductId
import com.palantir.gradle.dist.RecommendedProductDependencies
import com.palantir.gradle.dist.RecommendedProductDependency
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import com.palantir.slspackaging.versions.SlsProductVersions
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class CreateManifestTask extends DefaultTask {

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
        def jsonMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(new KebabCaseStrategy())
        def yamlMapper = new ObjectMapper(new YAMLFactory())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setPropertyNamingStrategy(new KebabCaseStrategy())

        def pdepCoords = []
        productDependenciesConfig.resolvedConfiguration
                .resolvedArtifacts
                .each { dep ->
                    def coord = identifierToCoord(dep.moduleVersion.id)
                    pdepCoords.add(project.dependencies.create("${coord}@pdep"))
                }
        Dependency[] pdepCoordsArr = pdepCoords.toArray(new Dependency[pdepCoords.size()])

        def pdepConfiguration = project.configurations.detachedConfiguration(pdepCoordsArr)

        Set<RecommendedProductDependency> allRecommendedProductDeps = []
        Map<String, Set<RecommendedProductDependency>> allRecommendedDepsByCoord = [:]
        Map<String, String> mavenCoordsByProductIds = [:]
        pdepConfiguration.resolvedConfiguration.lenientConfiguration.artifacts.each { pdepFile ->
            def coord = identifierToCoord(pdepFile.moduleVersion.id)
            def recommendedDeps = yamlMapper.readValue(pdepFile.file, RecommendedProductDependencies.class)

            if (!allRecommendedDepsByCoord.containsKey(coord)) {
                allRecommendedDepsByCoord.put(coord, new HashSet<RecommendedProductDependency>())
            }

            allRecommendedProductDeps.addAll(recommendedDeps.recommendedProductDependencies())
            allRecommendedDepsByCoord.get(coord).addAll(recommendedDeps.recommendedProductDependencies())

            recommendedDeps.recommendedProductDependencies().each { recommendedDep ->
                def productId = "${recommendedDep.productGroup()}:${recommendedDep.productName()}".toString()
                if (mavenCoordsByProductIds.containsKey(productId)) {
                    def othercoord = mavenCoordsByProductIds.get(productId)
                    throw new GradleException("Duplicate product dependency recommendations found for " +
                            "'${productId}' in '${coord}' and '${othercoord}'")
                }
                mavenCoordsByProductIds.put(productId, coord)
            }
        }

        Map<String, RecommendedProductDependency> recommendedDepsByProductId = [:]
        allRecommendedProductDeps.each { recommendedDep ->
            def productId = "${recommendedDep.productGroup()}:${recommendedDep.productName()}".toString()
            recommendedDepsByProductId.put(productId, recommendedDep)
        }

        Set<String> seenRecommendedProductIds = []
        def dependencies = []
        productDependencies.each { productDependency ->
            def productId = "${productDependency.productGroup}:${productDependency.productName}".toString()

            if (!productDependency.detectConstraints) {
                dependencies.add(jsonMapper.convertValue(productDependency, Map))
            } else {
                if (!recommendedDepsByProductId.containsKey(productId)) {
                    throw new GradleException("Product dependency '${productId}' has constraint detection enabled, " +
                            "but could not find any recommendations in ${productDependenciesConfig.name} configuration")
                }
                def recommendedProductDep = recommendedDepsByProductId.get(productId)
                try {
                    dependencies.add(jsonMapper.convertValue(
                            new ProductDependency(
                                    productDependency.productGroup,
                                    productDependency.productName,
                                    recommendedProductDep.minimumVersion(),
                                    recommendedProductDep.maximumVersion(),
                                    recommendedProductDep.recommendedVersion()),
                            Map))

                } catch (IllegalArgumentException e) {
                    def mavenCoordSource = mavenCoordsByProductIds.get(productId)
                    throw new GradleException("IllegalArgumentException encountered when generating " +
                            "ProductDependency from recommended dependency for ${productId} from ${mavenCoordSource}",
                            e)
                }
            }

            seenRecommendedProductIds.add(productId)
        }

        def unseenProductIds = new HashSet<>(recommendedDepsByProductId.keySet())
        seenRecommendedProductIds.each {unseenProductIds.remove(it)}
        ignoredProductIds.each {unseenProductIds.remove(it.toString())}

        if (!unseenProductIds.isEmpty()) {
            throw new GradleException("The following products are recommended as dependencies but do not appear in " +
                    "the product dependencies or product dependencies ignored list: ${unseenProductIds}")
        }

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
    }

    static String identifierToCoord(ModuleVersionIdentifier identifier) {
        return "$identifier.group:$identifier.name:$identifier.version"
    }

}
