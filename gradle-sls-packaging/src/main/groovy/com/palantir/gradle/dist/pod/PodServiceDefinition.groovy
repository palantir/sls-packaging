/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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
package com.palantir.gradle.dist.pod

import com.palantir.slspackaging.versions.SlsProductVersions
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import javax.annotation.Nonnull

@ToString
@EqualsAndHashCode
class PodServiceDefinition implements Serializable {

    private static final long serialVersionUID = 1L

    String productGroup = ""
    String productName = ""
    String productVersion = ""
    @Nonnull
    Map<String, String> volumeMap = [:]

    PodServiceDefinition() {}

    PodServiceDefinition(String productGroup, String productName, String productVersion, Map<String, String> volumeMap) {
        this.productGroup = productGroup
        this.productName = productName
        this.productVersion = productVersion
        this.volumeMap = volumeMap
    }

    def isValid() {
        if (productGroup.isEmpty()) {
            throw new IllegalArgumentException("product group must be specified for pod service")
        }
        if (productName.isEmpty()) {
            throw new IllegalArgumentException("product name must be specified for pod service")
        }
        if (!SlsProductVersions.isValidVersion(productVersion)) {
            throw new IllegalArgumentException("product version must be specified and be a valid SLS version for pod service")
        }
    }
}
