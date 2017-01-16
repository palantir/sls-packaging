package com.palantir.gradle.dist.asset

import com.palantir.gradle.dist.BaseDistributionExtension
import org.gradle.api.Project

class AssetDistributionExtension extends BaseDistributionExtension {

    private Map<String, String> assetDirs = [:]

    AssetDistributionExtension(Project project) {
        super(project)
        setProductType("asset.v1")
    }

    public Map<String, String> getAssetDirs() {
        return assetDirs
    }

    public void assetDir(String relativeSourcePath, String relativeDestinationPath) {
        this.assetDirs.put(relativeSourcePath, relativeDestinationPath)
    }

    public void setAssetDirs(Map<String, String> assets) {
        this.assetDirs = assets
    }
}
