package com.palantir.gradle.dist.asset

import com.palantir.gradle.dist.BaseDistributionExtension
import org.gradle.api.Project

class AssetDistributionExtension extends BaseDistributionExtension {

    private Map<String, String> assets = [:]

    AssetDistributionExtension(Project project) {
        super(project)
        productType("asset.v1")
    }

    public Map<String, String> getAssets() {
        return assets
    }

    public void assets(String relativeSourcePath) {
        this.assets.put(relativeSourcePath, relativeSourcePath)
    }

    public void assets(String relativeSourcePath, String relativeDestinationPath) {
        this.assets.put(relativeSourcePath, relativeDestinationPath)
    }

    public void setAssets(Map<String, String> assets) {
        this.assets = assets
    }
}
