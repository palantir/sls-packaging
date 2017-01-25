package com.palantir.gradle.dist.asset

import com.palantir.gradle.dist.BaseDistributionExtension
import org.gradle.api.Project

class AssetDistributionExtension extends BaseDistributionExtension {

    private Map<String, String> assets = [:]

    AssetDistributionExtension(Project project) {
        super(project)
        productType("asset.v1")
    }

    Map<String, String> getAssets() {
        return assets
    }

    void assets(String relativeSourcePath) {
        this.assets.put(relativeSourcePath, relativeSourcePath)
    }

    void assets(String relativeSourcePath, String relativeDestinationPath) {
        this.assets.put(relativeSourcePath, relativeDestinationPath)
    }

    void setAssets(Map<String, String> assets) {
        this.assets = assets
    }
}
