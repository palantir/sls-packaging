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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

@JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonProperty("optional")
    private boolean optional = false;

    /**
     * Module coordinate ({@code group:name}) whose resolved version should be used as {@link #minimumVersion}. The
     * resolution is performed by the plugin (against gradle-consistent-versions' locked classpath when present),
     * not the consumer; the resolved version is filled in before the manifest is written, so this field itself is
     * not serialised.
     */
    @JsonIgnore
    private String minimumVersionFrom;

    public ProductDependency() {}

    public ProductDependency(
            String productGroup,
            String productName,
            String minimumVersion,
            String maximumVersion,
            String recommendedVersion,
            boolean optional) {
        this.productGroup = productGroup;
        this.productName = productName;
        this.minimumVersion = minimumVersion;
        this.maximumVersion = maximumVersion;
        this.recommendedVersion = recommendedVersion;
        this.optional = optional;
        isValid();
    }

    public ProductDependency(
            String productGroup,
            String productName,
            String minimumVersion,
            String maximumVersion,
            String recommendedVersion) {
        this(productGroup, productName, minimumVersion, maximumVersion, recommendedVersion, false);
    }

    /**
     * We intentionally tolerate .dirty version strings for minimum and recommended version to ensure local development
     * remains tolerable.
     */
    public void isValid() {
        Preconditions.checkNotNull(productGroup, "productGroup must be specified");
        Preconditions.checkNotNull(productName, "productName must be specified");
        if (minimumVersionFrom != null) {
            Preconditions.checkArgument(
                    minimumVersion == null,
                    "Cannot specify both minimumVersion and minimumVersionFrom",
                    SafeArg.of("productGroup", productGroup),
                    SafeArg.of("productName", productName));
            String[] parts = minimumVersionFrom.split(":", -1);
            Preconditions.checkArgument(
                    parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty(),
                    "minimumVersionFrom must be in the format 'group:name'",
                    SafeArg.of("minimumVersionFrom", minimumVersionFrom),
                    SafeArg.of("productGroup", productGroup),
                    SafeArg.of("productName", productName));
            // Remaining validation runs after the minimum version is resolved.
            return;
        }
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
                    SafeArg.of("maximumVersion", maximumVersion),
                    SafeArg.of("productGroup", productGroup),
                    SafeArg.of("productName", productName));
        }

        // Minimum can be unset here if the minimumVersion is a non-orderable SLS version, e.g. "1.0.0.dirty"
        if (recommended.isPresent() && minimum.isPresent()) {
            Preconditions.checkArgument(
                    VersionComparator.INSTANCE.compare(recommended.get(), minimum.get()) >= 0,
                    "Recommended version is not greater than minimum version",
                    SafeArg.of("recommendedVersion", recommendedVersion),
                    SafeArg.of("minimumVersion", minimumVersion),
                    SafeArg.of("productGroup", productGroup),
                    SafeArg.of("productName", productName));
        }

        if (recommended.isPresent()) {
            Preconditions.checkArgument(
                    maximum.compare(recommended.get()) >= 0,
                    "Recommended version is greater than maximum version",
                    SafeArg.of("recommendedVersion", recommendedVersion),
                    SafeArg.of("maximumVersion", maximumVersion),
                    SafeArg.of("productGroup", productGroup),
                    SafeArg.of("productName", productName));
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
                "%s:%s(min: %s, recommended: %s, max: %s)%s",
                productGroup,
                productName,
                minimumVersion,
                recommendedVersion,
                maximumVersion,
                optional ? " optional" : "");
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

    public boolean getOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    public Optional<String> getMinimumVersionFrom() {
        return Optional.ofNullable(minimumVersionFrom);
    }

    public void setMinimumVersionFrom(String minimumVersionFrom) {
        this.minimumVersionFrom = minimumVersionFrom;
    }

    /** Generates a maximum version (e.g. {@code "1.x.x"}) from a minimum version (e.g. {@code "1.2.3"}). */
    public static String generateMaxVersion(String minimumVersion) {
        int firstDot = minimumVersion.indexOf('.');
        String major = firstDot < 0 ? minimumVersion : minimumVersion.substring(0, firstDot);
        return major + ".x.x";
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
                && maximumVersion.equals(that.maximumVersion)
                && optional == that.optional;
    }

    @Override
    public int hashCode() {
        return Objects.hash(productGroup, productName, minimumVersion, recommendedVersion, maximumVersion, optional);
    }
}
