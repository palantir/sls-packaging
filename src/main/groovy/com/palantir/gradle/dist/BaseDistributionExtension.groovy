package com.palantir.gradle.dist

import org.gradle.api.Project

class BaseDistributionExtension {

    private static final List<String> VALID_PRODUCT_TYPES = [
            "service.v1",
            "asset.v1",
            "daemon.v1"
    ]

    private final Project project
    private String serviceGroup
    private String serviceName
    private String productType = "service.v1"
    private Map<String, Object> manifestExtensions = [:]

    public BaseDistributionExtension(Project project) {
        this.project = project;
    }

    public void serviceName(String serviceName) {
        this.serviceName = serviceName
    }

    void serviceGroup(String serviceGroup) {
        this.serviceGroup = serviceGroup
    }

    public void setManifestExtensions(Map<String, Object> manifestExtensions) {
        this.manifestExtensions = manifestExtensions;
    }

    public void manifestExtensions(Map<String, Object> manifestExtensions) {
        this.manifestExtensions.putAll(manifestExtensions)
    }

    public String getServiceName() {
        return serviceName
    }

    public String getServiceGroup() {
        return serviceGroup ?: project.group
    }

    public Map<String, Object> getManifestExtensions() {
        return this.manifestExtensions;
    }

    public String getProductType() {
        return productType
    }

    public void setProductType(String type) {
        if (!VALID_PRODUCT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Invalid product type specified: " + type)
        }
        this.productType = type
    }
}
