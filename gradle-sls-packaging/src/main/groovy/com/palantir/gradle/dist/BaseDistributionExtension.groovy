package com.palantir.gradle.dist

import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

import java.util.regex.Matcher
import java.util.regex.Pattern

class BaseDistributionExtension {

    private static final Set<String> VALID_PRODUCT_TYPES = [
            "service.v1",
            "asset.v1"
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
    private String productType
    private Map<String, Object> manifestExtensions = [:]
    private List<ProductDependency> productDependencies = []

    BaseDistributionExtension(Project project) {
        this.project = project
    }

    void serviceName(String serviceName) {
        this.serviceName = serviceName
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
        productDependency(new ProductDependency(
                matcher.group("group"),
                matcher.group("name"),
                matcher.group("version"),
                null,
                recommendedVersion))
    }

    void productDependency(String serviceGroup, String serviceName, String minVersion, String maxVersion, String recommendedVersion = null) {
        productDependency(new ProductDependency(serviceGroup, serviceName, minVersion, maxVersion, recommendedVersion))
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

    String getServiceName() {
        return serviceName
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

}
