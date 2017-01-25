package com.palantir.gradle.dist.service

import com.palantir.gradle.dist.SlsProductVersions
import org.gradle.api.Nullable

class ServiceDependency {
    String group
    String name
    String minVersion
    String maxVersion
    @Nullable
    String recommendedVersion

    ServiceDependency() {}

    ServiceDependency(String group, String name, String minVersion, String maxVersion,
                      @Nullable String recommendedVersion) {
        this.group = group
        this.name = name
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
