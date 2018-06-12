/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.util.ConfigureUtil

import java.util.regex.Matcher
import java.util.regex.Pattern

class BaseDistributionExtension {

    private static final Set<String> VALID_PRODUCT_TYPES = [
            "service.v1",
            "daemon.v1",
            "asset.v1",
            "pod.v1"
    ]
    private static final Pattern MAVEN_COORDINATE_PATTERN = Pattern.compile(""
            + "(?<group>[^:@?]*):"
            + "(?<name>[^:@?]*):"
            + "(?<version>[^:@?]*)"
            + "(:(?<classifier>[^:@?]*))?"
            + "(@(?<type>[^:@?]*))?")

    private final Project project
    private String serviceGroup
    private String serviceName
    private String podName
    private String productType
    private Map<String, Object> manifestExtensions = [:]
    private List<ProductDependency> productDependencies = []
    private Configuration productDependenciesConfig
    private Set<ProductId> ignoredProductIds = []

    BaseDistributionExtension(Project project) {
        this.project = project
    }

    void serviceName(String serviceName) {
        this.serviceName = serviceName
    }

    void podName(String podName) {
        this.podName = podName
    }

    void serviceGroup(String serviceGroup) {
        this.serviceGroup = serviceGroup
    }

    void setManifestExtensions(Map<String, Object> manifestExtensions) {
        this.manifestExtensions = manifestExtensions
    }

    void manifestExtensions(Map<String, Object> manifestExtensions) {
        this.manifestExtensions.putAll(manifestExtensions)
    }

    void productType(String type) {
        if (!VALID_PRODUCT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Invalid product type '${type}' specified; supported types: ${VALID_PRODUCT_TYPES}.")
        }
        this.productType = type
    }

    void productDependency(String mavenCoordVersionRange, String recommendedVersion = null) {
        Matcher matcher = MAVEN_COORDINATE_PATTERN.matcher(mavenCoordVersionRange)
        if (!matcher.matches()) {
            throw new IllegalArgumentException("String '${mavenCoordVersionRange}' is not a valid maven coordinate. " +
                    "Must be in the format 'group:name:version:classifier@type', where ':classifier' and '@type' are " +
                    "optional.")
        }
        def minVersion = matcher.group("version")
        productDependency(new ProductDependency(
                matcher.group("group"),
                matcher.group("name"),
                minVersion,
                generateMaxVersion(minVersion),
                recommendedVersion))
    }

    void productDependency(String serviceGroup, String serviceName, String minVersion, String maxVersion = null, String recommendedVersion = null) {
        productDependency(new ProductDependency(
                serviceGroup,
                serviceName,
                minVersion,
                maxVersion == null
                    ? generateMaxVersion(minVersion)
                    : maxVersion,
                recommendedVersion))
    }

    void productDependency(Closure closure) {
        ProductDependency dep = new ProductDependency()
        ConfigureUtil.configureUsing(closure).execute(dep)
        dep.isValid()
        productDependency(dep)
    }

    void productDependency(ProductDependency dependency) {
        productDependencies.add(dependency)
    }

    void setProductDependenciesConfig(Configuration productDependenciesConfig) {
        this.productDependenciesConfig = productDependenciesConfig
    }

    void ignoredProductDependency(String productGroup, String productName) {
        ignoredProductIds.add(new ProductId(productGroup, productName))
    }

    String getServiceName() {
        return serviceName ?: project.name
    }

    String getPodName() {
        return podName ?: project.name
    }

    String getServiceGroup() {
        return serviceGroup ?: project.group
    }

    Map<String, Object> getManifestExtensions() {
        return this.manifestExtensions
    }

    String getProductType() {
        return productType
    }

    List<ProductDependency> getServiceDependencies() {
        return productDependencies
    }

    Configuration getProductDependenciesConfig() {
        return productDependenciesConfig
    }

    Set<ProductId> getIgnoredProductIds() {
        return ignoredProductIds
    }

    static String generateMaxVersion(String minimumVersion) {
        def minimumVersionMajorRev = minimumVersion.tokenize('.')[0].toInteger()
        return "${minimumVersionMajorRev}.x.x"
    }

}
