package com.palantir.gradle.dist

import com.palantir.slspackaging.versions.SlsProductVersions
import org.gradle.api.Nullable

class ServiceDependency {
    String productGroup
    String productName
    @Nullable
    String minVersion
    @Nullable
    String maxVersion
    @Nullable
    String recommendedVersion

    ServiceDependency() {}

    ServiceDependency(String productGroup, String productName, @Nullable String minVersion,
                      @Nullable String maxVersion, @Nullable String recommendedVersion) {
        this.productGroup = productGroup
        this.productName = productName
        this.minVersion = minVersion
        this.maxVersion = maxVersion
        this.recommendedVersion = recommendedVersion
        isValid()
    }

    def isValid() {
        [maxVersion].each {
            if (it && !SlsProductVersions.isValidVersionOrMatcher(it)) {
                throw new IllegalArgumentException(
                        "maxVersion must be valid SLS version or version matcher: " + it)
            }
        }

        [minVersion, recommendedVersion].each {
            if (it && !SlsProductVersions.isValidVersion(it)) {
                throw new IllegalArgumentException(
                        "minVersion and recommendedVersions must be valid SLS versions: " + it)
            }
        }
    }
}
