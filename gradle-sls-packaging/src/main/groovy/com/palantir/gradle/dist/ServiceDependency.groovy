package com.palantir.gradle.dist

import com.palantir.slspackaging.versions.SlsProductVersions
import org.gradle.api.Nullable

class ServiceDependency {
    String productGroup
    String productName
    String minVersion
    String maxVersion
    @Nullable
    String recommendedVersion

    ServiceDependency() {}

    ServiceDependency(String productGroup, String productName, String minVersion, String maxVersion,
                      @Nullable String recommendedVersion) {
        this.productGroup = productGroup
        this.productName = productName
        this.minVersion = minVersion
        this.maxVersion = maxVersion
        this.recommendedVersion = recommendedVersion
    }

    def verifyVersions() {
        [minVersion, maxVersion, recommendedVersion].each {
            if (it && !SlsProductVersions.isValidVersion(it)) {
                throw new IllegalArgumentException("Invalid SLS version: " + it)
            }
        }
    }
}
