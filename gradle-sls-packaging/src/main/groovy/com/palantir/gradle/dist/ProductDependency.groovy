package com.palantir.gradle.dist

import com.fasterxml.jackson.annotation.JsonIgnore
import com.palantir.slspackaging.versions.SlsProductVersions
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.api.Nullable

@ToString
@EqualsAndHashCode
class ProductDependency implements Serializable {

    private static final long serialVersionUID = 1L

    String productGroup
    String productName

    @JsonIgnore
    boolean detectConstraints

    @Nullable
    String minimumVersion
    @Nullable
    String maximumVersion
    @Nullable
    String recommendedVersion

    ProductDependency() {}

    ProductDependency(String productGroup, String productName) {
        this.productGroup = productGroup
        this.productName = productName
        this.detectConstraints = true
        isValid()
    }

    ProductDependency(
            String productGroup,
            String productName,
            @Nullable String minimumVersion,
            @Nullable String maximumVersion,
            @Nullable String recommendedVersion) {
        this.productGroup = productGroup
        this.productName = productName
        this.detectConstraints = false
        this.minimumVersion = minimumVersion
        this.maximumVersion = maximumVersion
        this.recommendedVersion = recommendedVersion
        isValid()
    }

    def isValid() {
        if (detectConstraints) {
            if (minimumVersion != null) {
                throw new IllegalArgumentException("minimum version must not be specified if detectConstraints is enabled")
            }
            if (maximumVersion != null) {
                throw new IllegalArgumentException("maximum version must not be specified if detectConstraints is enabled")
            }
            if (recommendedVersion != null) {
                throw new IllegalArgumentException("recommended version must not be specified if detectConstraints is enabled")
            }
        } else {
            if (minimumVersion == null) {
                throw new IllegalArgumentException("minimum version must be specified");
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

}
