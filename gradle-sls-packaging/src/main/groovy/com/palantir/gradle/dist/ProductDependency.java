/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import com.google.common.base.Preconditions;
import com.palantir.sls.versions.OrderableSlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import com.palantir.sls.versions.VersionComparator;
import java.io.Serializable;
import java.util.Optional;
import org.immutables.value.Value;

/** Fully validated version of {@link com.palantir.gradle.dist.RawProductDependency}. */
@Value.Immutable
public interface ProductDependency extends Serializable {

    long serialVersionUID = 1L;

    @JsonProperty("product-group")
    String getProductGroup();

    @JsonProperty("product-name")
    String getProductName();

    @JsonProperty("minimum-version")
    OrderableSlsVersion getMinimumVersion();

    @JsonProperty("recommended-version")
    Optional<OrderableSlsVersion> getRecommendedVersion();

    @JsonProperty("maximum-version")
    SlsVersionMatcher getMaximumVersion();

    @Value.Check
    default void check() {
        Preconditions.checkArgument(getMaximumVersion().compare(getMinimumVersion()) >= 0,
                "Minimum version (%s) is greater than maximum version (%s)",
                getMinimumVersion(), getMaximumVersion());

        getRecommendedVersion().ifPresent(recommended -> {
            Preconditions.checkArgument(
                    VersionComparator.INSTANCE.compare(recommended, getMinimumVersion()) >= 0,
                    "Recommended version (%s) is not greater than minimum version (%s)",
                    recommended, getMinimumVersion());
            Preconditions.checkArgument(
                    getMaximumVersion().compare(recommended) >= 0,
                    "Recommended version (%s) is greater than maximum version (%s)",
                    recommended, getMaximumVersion());
        });

        Preconditions.checkArgument(
                !getMinimumVersion().toString().equals(getMaximumVersion().toString()),
                "minimumVersion and maximumVersion must be different in product dependency on %s. This prevents a "
                        + "known antipattern where services declare themselves to require a lockstep upgrade.",
                getProductName());
    }
}
