package com.palantir.gradle.dist.asset

import com.palantir.gradle.dist.BaseDistributionExtension
import org.gradle.api.Project

class AssetDistributionExtension extends BaseDistributionExtension {

    private Map<String, String> assetsDirs = [:]

    AssetDistributionExtension(Project project) {
        super(project)
        productType("asset.v1")
    }

    public Map<String, String> getAssetsDirs() {
        return assetsDirs
    }

    public void assetsDir(String relativeSourcePath, String relativeDestinationPath) {
        this.assetsDirs.put(relativeSourcePath, relativeDestinationPath)
    }

    public void setAssetsDirs(Map<String, String> assets) {
        this.assetsDirs = assets
    }
}
