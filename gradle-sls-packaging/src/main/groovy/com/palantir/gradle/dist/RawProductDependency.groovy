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

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.base.Preconditions
import com.palantir.sls.versions.OrderableSlsVersion
import com.palantir.sls.versions.SlsVersionMatcher
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import javax.annotation.Nullable

@ToString
@EqualsAndHashCode
@CompileStatic
class RawProductDependency implements Serializable {

    private static final long serialVersionUID = 1L

    @JsonProperty("product-group")
    String productGroup

    @JsonProperty("product-name")
    String productName

    @JsonProperty("minimum-version")
    String minimumVersion

    @JsonProperty("recommended-version")
    @Nullable
    String recommendedVersion

    @JsonProperty("maximum-version")
    String maximumVersion

    RawProductDependency() {}

    RawProductDependency(
            String productGroup,
            String productName,
            String minimumVersion,
            String maximumVersion,
            String recommendedVersion) {
        this.productGroup = productGroup
        this.productName = productName
        this.minimumVersion = minimumVersion
        this.maximumVersion = maximumVersion
        this.recommendedVersion = recommendedVersion
        validate()
    }

    ProductDependency validate() {
        def maximumOpt = SlsVersionMatcher.safeValueOf(maximumVersion)
        Preconditions.checkArgument(
                maximumOpt.isPresent(), "maximumVersion must be a valid version matcher: " + maximumVersion)
        Preconditions.checkArgument(
                OrderableSlsVersion.check(minimumVersion),
                "minimumVersion must be an orderable SLS version: " + minimumVersion)

        if (recommendedVersion) {
            Preconditions.checkArgument(
                    OrderableSlsVersion.check(recommendedVersion),
                    "recommendedVersion must be an orderable SLS version: " + recommendedVersion)
        }

        return ImmutableProductDependency.builder()
                .productGroup(productGroup)
                .productName(productName)
                .minimumVersion(OrderableSlsVersion.valueOf(minimumVersion))
                .recommendedVersion(Optional.ofNullable(recommendedVersion).map({s -> OrderableSlsVersion.valueOf(s)}))
                .maximumVersion(SlsVersionMatcher.valueOf(maximumVersion))
                .build()
    }
}
