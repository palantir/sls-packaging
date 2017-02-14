package com.palantir.gradle.dist

import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class BaseDistributionExtension {

    private static final Set<String> VALID_PRODUCT_TYPES = [
            "service.v1",
            "asset.v1"
    ]

    private final Project project
    private String serviceGroup
    private String serviceName
    private String productType
    private Map<String, Object> manifestExtensions = [:]
    private List<ServiceDependency> serviceDependencies = []

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

    void serviceDependency(String serviceGroup, String serviceName, String minVersion, String maxVersion) {
        serviceDependency(new ServiceDependency(serviceGroup, serviceName, minVersion, maxVersion, null))
    }

    void serviceDependency(String serviceGroup, String serviceName, String minVersion, String maxVersion, String recommendedVersion) {
        serviceDependency(new ServiceDependency(serviceGroup, serviceName, minVersion, maxVersion, recommendedVersion))
    }

    void serviceDependency(Closure closure) {
        ServiceDependency dep = new ServiceDependency()
        ConfigureUtil.configureUsing(closure).execute(dep)
        serviceDependency(dep)
    }

    void serviceDependency(ServiceDependency dependency) {
        serviceDependencies.add(dependency)
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

    List<ServiceDependency> getServiceDependencies() {
        return serviceDependencies
    }
}
