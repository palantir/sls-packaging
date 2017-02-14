package com.palantir.gradle.dist

import com.palantir.slspackaging.versions.SlsProductVersions
import org.gradle.api.Nullable

class ProductDependency {
    String productGroup
    String productName
    @Nullable
    String minimumVersion
    @Nullable
    String maximumVersion
    @Nullable
    String recommendedVersion

    ProductDependency() {}

    ProductDependency(String productGroup, String productName, @Nullable String minimumVersion,
                      @Nullable String maximumVersion, @Nullable String recommendedVersion) {
        this.productGroup = productGroup
        this.productName = productName
        this.minimumVersion = minimumVersion
        this.maximumVersion = maximumVersion
        this.recommendedVersion = recommendedVersion
        isValid()
    }

    def isValid() {
        [maximumVersion].each {
            if (it && !SlsProductVersions.isValidVersionOrMatcher(it)) {
                throw new IllegalArgumentException(
                        "maximumVersion must be valid SLS version or version matcher: " + it)
            }
        }

        [minimumVersion, recommendedVersion].each {
            if (it && !SlsProductVersions.isValidVersion(it)) {
                throw new IllegalArgumentException(
                        "minimumVersion and recommendedVersions must be valid SLS versions: " + it)
            }
        }
    }
}
