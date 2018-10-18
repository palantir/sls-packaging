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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.palantir.gradle.dist.ProductDependency
import com.palantir.gradle.dist.ProductId
import com.palantir.gradle.dist.RecommendedProductDependencies
import com.palantir.gradle.dist.RecommendedProductDependency
import com.palantir.gradle.dist.RecommendedProductDependencyMerger
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import com.palantir.slspackaging.versions.SlsProductVersions
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import java.util.jar.Manifest
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CompileStatic
class CreateManifestTask extends DefaultTask {

    public static String SLS_RECOMMENDED_PRODUCT_DEPS_KEY = "Sls-Recommended-Product-Dependencies"
    public static ObjectMapper jsonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)
    public static final String PDEPS_FILE_PATH = "META-INF/pdeps.json"

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
        Map<String, Set<RecommendedProductDependency>> allRecommendedDepsByCoord = [:]
        Map<String, String> mavenCoordsByProductIds = [:]
        Map<String, RecommendedProductDependency> recommendedDepsByProductId = [:]

        productDependenciesConfig.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            String coord = identifierToCoord(artifact.moduleVersion.id)

            def recommendedDeps = readProductDepsFromPdepsFile(artifact) ?: readProductDepsFromManifest(artifact)
            if (recommendedDeps == null) {
                return
            }

            if (!allRecommendedDepsByCoord.containsKey(coord)) {
                allRecommendedDepsByCoord.put(coord, new HashSet<RecommendedProductDependency>())
            }

            allRecommendedDepsByCoord.get(coord).addAll(recommendedDeps.recommendedProductDependencies())

            recommendedDeps.recommendedProductDependencies().each { recommendedDep ->
                def productId = "${recommendedDep.productGroup}:${recommendedDep.productName}".toString()
                def existingDep = recommendedDepsByProductId.get(productId)
                def dep
                if (mavenCoordsByProductIds.containsKey(productId)
                        && !Objects.equals(existingDep, recommendedDep)) {
                    def othercoord = mavenCoordsByProductIds.get(productId)
                    // Try to merge
                    logger.info(
                            "Trying to merge duplicate product dependencies found for {} in '{}' and '{}': {} and {}",
                            productId,
                            coord,
                            othercoord,
                            recommendedDep,
                            existingDep)
                    dep = RecommendedProductDependencyMerger.merge(recommendedDep, existingDep)
                } else {
                    dep = recommendedDep
                }
                mavenCoordsByProductIds.put(productId, coord)
                recommendedDepsByProductId.put(productId, dep)
            }
        }

        Set<String> seenRecommendedProductIds = []
        def dependencies = []
        productDependencies.each { productDependency ->
            def productId = "${productDependency.productGroup}:${productDependency.productName}".toString()

            if (!productDependency.detectConstraints) {
                dependencies.add(jsonMapper.convertValue(productDependency, new TypeReference<Map<String, Object>>() {}))
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
                                    recommendedProductDep.minimumVersion,
                                    recommendedProductDep.maximumVersion,
                                    recommendedProductDep.recommendedVersion),
                            new TypeReference<Map<String, Object>>() {}))

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
        seenRecommendedProductIds.each { unseenProductIds.remove(it) }
        ignoredProductIds?.each { unseenProductIds.remove(it.toString()) }
        unseenProductIds.remove("${serviceGroup}:${serviceName}".toString())

        if (!unseenProductIds.isEmpty()) {
            throw new GradleException("The following products are recommended as dependencies but do not appear in " +
                    "the product dependencies or product dependencies ignored list: ${unseenProductIds}. See gradle-sls-packaging for more details")
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

    RecommendedProductDependencies readProductDepsFromManifest(ResolvedArtifact artifact) {
        def coord = identifierToCoord(artifact.moduleVersion.id)
        def manifest
        try {
            def zf = new ZipFile(artifact.file)
            def manifestEntry = zf.getEntry("META-INF/MANIFEST.MF")
            if (manifestEntry == null) {
                logger.debug("Manifest file does not exist in jar for '{}'", coord)
                return
            }
            manifest = new Manifest(zf.getInputStream(manifestEntry))
        } catch (IOException e) {
            logger.warn("IOException encountered when processing artifact '{}', file '{}'", coord, artifact.file, e)
            return
        }

        def pdeps = manifest.getMainAttributes().getValue(SLS_RECOMMENDED_PRODUCT_DEPS_KEY)

        if (pdeps == null) {
            logger.debug("No pdeps found in manifest for artifact '{}', file '{}'", coord, artifact.file)
            return null
        }

        return jsonMapper.readValue(pdeps, RecommendedProductDependencies)
    }

    RecommendedProductDependencies readProductDepsFromPdepsFile(ResolvedArtifact artifact) {
        def coord = identifierToCoord(artifact.moduleVersion.id)
        try {
            def zf = new ZipFile(artifact.file)
            def entry = zf.getEntry(PDEPS_FILE_PATH)
            if (entry == null) {
                logger.debug("Pdeps file {} does not exist in jar for '{}'", PDEPS_FILE_PATH, coord)
                return null
            }

            def dep = jsonMapper.readValue(zf.getInputStream(entry), RecommendedProductDependency)
            return RecommendedProductDependencies.builder().addRecommendedProductDependencies(dep).build()
        } catch (IOException e) {
            logger.warn("IOException encountered when processing artifact '{}', file '{}'", coord, artifact.file, e)
        }
        return null
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
        this.productDependencies = productDependencies as Set
        this.manifestExtensions = manifestExtensions
        this.productDependenciesConfig = productDependenciesConfig
        this.ignoredProductIds = ignoredProductIds
        dependsOn(productDependenciesConfig)
    }

    @CompileStatic
    static String identifierToCoord(ModuleVersionIdentifier identifier) {
        return "${identifier.group}:${identifier.name}:${identifier.version}".toString()
    }

}
