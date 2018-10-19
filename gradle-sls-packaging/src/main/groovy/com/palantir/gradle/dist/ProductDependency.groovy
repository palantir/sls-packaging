/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.dist

import com.fasterxml.jackson.annotation.JsonIgnore
import com.palantir.slspackaging.versions.SlsProductVersions
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import javax.annotation.Nullable

@ToString
@EqualsAndHashCode
@CompileStatic
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
