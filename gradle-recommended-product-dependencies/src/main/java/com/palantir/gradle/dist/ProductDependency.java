/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.sls.versions.OrderableSlsVersion;
import com.palantir.sls.versions.SlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import com.palantir.sls.versions.VersionComparator;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

public final class ProductDependency implements Serializable {
    @JsonProperty("product-group")
    private String productGroup;

    @JsonProperty("product-name")
    private String productName;

    @JsonProperty("minimum-version")
    private String minimumVersion;

    @JsonProperty("recommended-version")
    @Nullable
    private String recommendedVersion;

    @JsonProperty("maximum-version")
    private String maximumVersion;

    public ProductDependency() {}

    public ProductDependency(
            String productGroup,
            String productName,
            String minimumVersion,
            String maximumVersion,
            String recommendedVersion) {
        this.productGroup = productGroup;
        this.productName = productName;
        this.minimumVersion = minimumVersion;
        this.maximumVersion = maximumVersion;
        this.recommendedVersion = recommendedVersion;
        isValid();
    }

    /**
     * We intentionally tolerate .dirty version strings for minimum and recommended version to ensure local development
     * remains tolerable.
     */
    public void isValid() {
        Preconditions.checkNotNull(productGroup, "productGroup must be specified");
        Preconditions.checkNotNull(productName, "productName must be specified");
        Optional<OrderableSlsVersion> minimum = parseMinimum();
        Optional<OrderableSlsVersion> recommended = parseRecommended();
        SlsVersionMatcher maximum = parseMaximum();

        Preconditions.checkArgument(
                !minimumVersion.equals(maximumVersion),
                "minimumVersion and maximumVersion must be different. This prevents a "
                        + "known antipattern where services declare themselves to require a lockstep upgrade.",
                SafeArg.of("productGroup", productGroup),
                SafeArg.of("productName", productName));

        if (minimum.isPresent()) {
            Preconditions.checkArgument(
                    maximum.compare(minimum.get()) >= 0,
                    "Minimum version is greater than maximum version",
                    SafeArg.of("minimumVersion", minimumVersion),
                    SafeArg.of("maximumVersion", maximumVersion));
        }

        // Minimum can be unset here if the minimumVersion is a non-orderable SLS version, e.g. "1.0.0.dirty"
        if (recommended.isPresent() && minimum.isPresent()) {
            Preconditions.checkArgument(
                    VersionComparator.INSTANCE.compare(recommended.get(), minimum.get()) >= 0,
                    "Recommended version is not greater than minimum version",
                    SafeArg.of("recommendedVersion", recommendedVersion),
                    SafeArg.of("minimumVersion", minimumVersion));
        }

        if (recommended.isPresent()) {
            Preconditions.checkArgument(
                    maximum.compare(recommended.get()) >= 0,
                    "Recommended version is greater than maximum version",
                    SafeArg.of("recommendedVersion", recommendedVersion),
                    SafeArg.of("maximumVersion", maximumVersion));
        }
    }

    public Optional<OrderableSlsVersion> parseRecommended() {
        if (recommendedVersion == null) {
            return Optional.empty();
        }

        Preconditions.checkArgument(
                SlsVersion.check(recommendedVersion),
                "recommendedVersion must be a valid SLS version",
                SafeArg.of("recommendedVersion", recommendedVersion),
                SafeArg.of("productGroup", productGroup),
                SafeArg.of("productName", productName));

        return OrderableSlsVersion.safeValueOf(recommendedVersion);
    }

    public Optional<OrderableSlsVersion> parseMinimum() {
        Preconditions.checkNotNull(minimumVersion, "minimumVersion must be specified");

        Preconditions.checkArgument(
                SlsVersion.check(minimumVersion),
                "minimumVersion must be an SLS version",
                SafeArg.of("minimumVersion", minimumVersion),
                SafeArg.of("productGroup", productGroup),
                SafeArg.of("productName", productName));

        return OrderableSlsVersion.safeValueOf(minimumVersion);
    }

    public SlsVersionMatcher parseMaximum() {
        Preconditions.checkNotNull(maximumVersion, "maximumVersion must be specified");

        Optional<SlsVersionMatcher> maximumOpt = SlsVersionMatcher.safeValueOf(maximumVersion);
        Preconditions.checkArgument(
                maximumOpt.isPresent(),
                "maximumVersion must be a valid version matcher",
                SafeArg.of("maximumVersion", maximumVersion),
                SafeArg.of("productGroup", productGroup),
                SafeArg.of("productName", productName));

        return maximumOpt.get();
    }

    @Override
    public String toString() {
        return String.format(
                "%s:%s(min: %s, recommended: %s, max: %s)",
                productGroup, productName, minimumVersion, recommendedVersion, maximumVersion);
    }

    public String getProductGroup() {
        return productGroup;
    }

    public void setProductGroup(String productGroup) {
        this.productGroup = productGroup;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getMinimumVersion() {
        return minimumVersion;
    }

    public void setMinimumVersion(String minimumVersion) {
        this.minimumVersion = minimumVersion;
    }

    public String getRecommendedVersion() {
        return recommendedVersion;
    }

    public void setRecommendedVersion(String recommendedVersion) {
        this.recommendedVersion = recommendedVersion;
    }

    public String getMaximumVersion() {
        return maximumVersion;
    }

    public void setMaximumVersion(String maximumVersion) {
        this.maximumVersion = maximumVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ProductDependency that = (ProductDependency) other;
        return productGroup.equals(that.productGroup)
                && productName.equals(that.productName)
                && minimumVersion.equals(that.minimumVersion)
                && Objects.equals(recommendedVersion, that.recommendedVersion)
                && maximumVersion.equals(that.maximumVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productGroup, productName, minimumVersion, recommendedVersion, maximumVersion);
    }
}
