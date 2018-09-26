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

package com.palantir.gradle.dist

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.palantir.gradle.dist.tasks.CreateManifestTask
import java.util.jar.Manifest
import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

final class DependenciesUtil {

    public static ObjectMapper jsonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)

    static List<ProductDependency> resolveDependencies(
            Project project,
            Configuration dependenciesConfig,
            Set<ProductDependency> productDependencies,
            Set<ProductId> ignoredProductIds) {

        def logger = project.logger

        Map<String, Set<RecommendedProductDependency>> allRecommendedDepsByCoord = [:]
        Map<String, String> mavenCoordsByProductIds = [:]
        Map<String, RecommendedProductDependency> recommendedDepsByProductId = [:]

        dependenciesConfig.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            String coord = CreateManifestTask.identifierToCoord(artifact.moduleVersion.id)

            def manifest
            try {
                def zf = new ZipFile(artifact.file)
                def manifestEntry = zf.getEntry("META-INF/MANIFEST.MF")
                if (manifestEntry == null) {
                    logger.debug("Manifest file does not exist in jar for '${coord}'")
                    return
                }
                manifest = new Manifest(zf.getInputStream(manifestEntry))
            } catch (IOException e) {
                logger.warn("IOException encountered when processing artifact '{}', file '{}'", coord, artifact.file, e)
                return
            }

            def pdeps = manifest.getMainAttributes().getValue(CreateManifestTask.SLS_RECOMMENDED_PRODUCT_DEPS_KEY)

            if (pdeps == null) {
                logger.debug("No pdeps found in manifest for artifact '{}', file '{}'", coord, artifact.file)
                return
            }

            def recommendedDeps = jsonMapper.readValue(pdeps, RecommendedProductDependencies.class)

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
        List<ProductDependency> dependencies = []
        productDependencies.each { productDependency ->
            def productId = "${productDependency.productGroup}:${productDependency.productName}".toString()

            if (!productDependency.detectConstraints) {
                dependencies.add(productDependency)
            } else {
                if (!recommendedDepsByProductId.containsKey(productId)) {
                    throw new GradleException("Product dependency '${productId}' has constraint detection enabled, " +
                            "but could not find any recommendations in ${dependenciesConfig.name} configuration")
                }
                def recommendedProductDep = recommendedDepsByProductId.get(productId)
                try {
                    dependencies.add(
                            new ProductDependency(
                                    productDependency.productGroup,
                                    productDependency.productName,
                                    recommendedProductDep.minimumVersion,
                                    recommendedProductDep.maximumVersion,
                                    recommendedProductDep.recommendedVersion))

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
        ignoredProductIds.each { unseenProductIds.remove(it.toString()) }
        // TODO...
        unseenProductIds.remove("${serviceGroup}:${serviceName}".toString())

        if (!unseenProductIds.isEmpty()) {
            throw new GradleException("The following products are recommended as dependencies but do not appear in " +
                    "the product dependencies or product dependencies ignored list: ${unseenProductIds}. See " +
                    "gradle-sls-packaging for more details")
        }
        dependencies
    }
}
