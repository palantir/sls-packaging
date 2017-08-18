package com.palantir.gradle.dist

import com.fasterxml.jackson.annotation.JsonProperty
import com.palantir.slspackaging.versions.SlsProductVersions
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString
@EqualsAndHashCode
class RecommendedProductDependency {

    @JsonProperty("product-group")
    String productGroup

    @JsonProperty("product-name")
    String productName

    @JsonProperty("minimum-version")
    String minimumVersion

    @JsonProperty("maximum-version")
    String maximumVersion

    @JsonProperty("recommended-version")
    String recommendedVersion

    RecommendedProductDependency() {}

    void isValid() {
        if (productGroup == null) {
            throw new IllegalArgumentException("productGroup must be specified for a recommended product dependency")
        }
        if (productName == null) {
            throw new IllegalArgumentException("productName must be specified for a recommended product dependency")
        }
        if (minimumVersion == null) {
            throw new IllegalArgumentException("minimum version must be specified")
        }
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
        if (minimumVersion == maximumVersion) {
            throw new IllegalArgumentException("minimumVersion and maximumVersion must be different "
                    + "in product dependency on " + this.productName)
        }
    }

}
