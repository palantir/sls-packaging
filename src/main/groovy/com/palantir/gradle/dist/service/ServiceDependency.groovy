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

    private ServiceDependency(String group, String name, String minVersion, String maxVersion, String recommendedVersion) {
        this.group = group
        this.name = name
        this.minVersion = minVersion
        this.maxVersion = maxVersion
        this.recommendedVersion = recommendedVersion

        [minVersion, maxVersion, recommendedVersion].each {
            if (it && !SlsProductVersions.isValidVersion(it)) {
                throw new IllegalArgumentException("Invalid SLS version: " + it)
            }
        }
    }

    static ServiceDependency of(String group, String name, String minVersion, String maxVersion,
                                       @Nullable String recommendedVersion) {
        return new ServiceDependency(group, name, minVersion, maxVersion, recommendedVersion)
    }
}
