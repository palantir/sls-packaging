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
import com.palantir.logsafe.Preconditions
import com.palantir.logsafe.SafeArg
import com.palantir.sls.versions.OrderableSlsVersion
import com.palantir.sls.versions.SlsVersion
import com.palantir.sls.versions.SlsVersionMatcher
import com.palantir.sls.versions.VersionComparator
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import javax.annotation.Nullable

@ToString
@EqualsAndHashCode
@CompileStatic
class ProductDependency implements Serializable {

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

    ProductDependency() {}

    ProductDependency(
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
        isValid()
    }

    /**
     * We intentionally tolerate .dirty version strings for minimum and recommended version
     * to ensure local development remains tolerable.
     */
    def isValid() {
        Preconditions.checkNotNull(productGroup, "productGroup must be specified")
        Preconditions.checkNotNull(productName, "productName must be specified")
        OrderableSlsVersion minimum = parseMinimum()
        Optional<OrderableSlsVersion> recommended = parseRecommended()
        SlsVersionMatcher maximum = parseMaximum()

        Preconditions.checkArgument(
                minimumVersion != maximumVersion,
                "minimumVersion and maximumVersion must be different. This prevents a "
                        + "known antipattern where services declare themselves to require a lockstep upgrade.",
                SafeArg.of("productGroup", productGroup),
                SafeArg.of("productName", productName))

        Preconditions.checkArgument(
                maximum.compare(minimum) >= 0,
                "Minimum version is greater than maximum version",
                SafeArg.of("minimumVersion", minimumVersion),
                SafeArg.of("maximumVersion", maximumVersion))

        if (recommended.isPresent()) {
            Preconditions.checkArgument(
                    VersionComparator.INSTANCE.compare(recommended.get(), minimum) >= 0,
                    "Recommended version is not greater than minimum version",
                    SafeArg.of("recommendedVersion", recommendedVersion),
                    SafeArg.of("minimumVersion", minimumVersion))
        }

        if (recommended.isPresent()) {
            Preconditions.checkArgument(
                    maximum.compare(recommended.get()) >= 0,
                    "Recommended version is greater than maximum version",
                    SafeArg.of("recommendedVersion", recommendedVersion),
                    SafeArg.of("maximumVersion", maximumVersion))
        }
    }

    Optional<OrderableSlsVersion> parseRecommended() {
        if (recommendedVersion == null) {
            return Optional.empty()
        }

        Preconditions.checkArgument(
                SlsVersion.check(recommendedVersion),
                "recommendedVersion must be a valid SLS version",
                SafeArg.of("recommendedVersion", recommendedVersion),
                SafeArg.of("productGroup", productGroup),
                SafeArg.of("productName", productName))

        return OrderableSlsVersion.safeValueOf(recommendedVersion)
    }

    OrderableSlsVersion parseMinimum() {
        Preconditions.checkNotNull(minimumVersion, "minimumVersion must be specified")

        def result = OrderableSlsVersion.safeValueOf(minimumVersion)
        Preconditions.checkArgument(
                result.isPresent(),
                "minimumVersion must be an orderable SLS version",
                SafeArg.of("minimumVersion", minimumVersion),
                SafeArg.of("productGroup", productGroup),
                SafeArg.of("productName", productName))

        return result.get()
    }

    SlsVersionMatcher parseMaximum() {
        Preconditions.checkNotNull(maximumVersion, "maximumVersion must be specified")

        def maximumOpt = SlsVersionMatcher.safeValueOf(maximumVersion)
        Preconditions.checkArgument(
                maximumOpt.isPresent(),
                "maximumVersion must be a valid version matcher",
                SafeArg.of("maximumVersion", maximumVersion),
                SafeArg.of("productGroup", productGroup),
                SafeArg.of("productName", productName))

        return maximumOpt.get()
    }

    @Override
    String toString() {
        return String.format("%s:%s(min: %s, recommended: %s, max: %s)",
                productGroup,
                productName,
                minimumVersion,
                recommendedVersion,
                maximumVersion)
    }
}
