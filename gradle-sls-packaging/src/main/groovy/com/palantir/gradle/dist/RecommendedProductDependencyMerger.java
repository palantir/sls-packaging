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

package com.palantir.gradle.dist;

import com.palantir.sls.versions.OrderableSlsVersion;
import com.palantir.sls.versions.VersionComparator;
import com.palantir.slspackaging.versions.SlsProductVersions;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class RecommendedProductDependencyMerger {
    private RecommendedProductDependencyMerger() { }

    public static RecommendedProductDependency merge(
            RecommendedProductDependency dep1, RecommendedProductDependency dep2) {
        // Ensure they are valid
        Arrays.asList(dep1, dep2).forEach(RecommendedProductDependency::isValid);
        if (!dep1.getProductGroup().equals(dep2.getProductGroup())) {
            throw new IllegalArgumentException(String.format("Product groups differ: '%s' and '%s'",
                dep1.getProductGroup(), dep2.getProductGroup()));
        }
        if (!dep1.getProductName().equals(dep2.getProductName())) {
            throw new IllegalArgumentException(String.format("Product names differ: '%s' and '%s'",
                dep1.getProductName(), dep2.getProductName()));
        }

        OrderableSlsVersion minimumVersion = Stream.of(dep1.getMinimumVersion(), dep2.getMinimumVersion())
                .filter(SlsProductVersions::isOrderableVersion)
                .map(OrderableSlsVersion::valueOf)
                .max(VersionComparator.INSTANCE)
                .orElseThrow(() -> new RuntimeException("Unable to determine minimum version"));

        Optional<MaximumVersion> maximumVersion = Stream
                .of(dep1.getMaximumVersion(), dep2.getMaximumVersion())
                .filter(Objects::nonNull)
                .map(MaximumVersion::valueOf)
                .min(MaximumVersionComparator.INSTANCE);

        // Sanity check: min has to be <= max
        if (!satisfiesMaxVersion(maximumVersion, minimumVersion)) {
            throw new IllegalArgumentException(String.format(
            "Could not merge recommended product dependencies as their version ranges do not overlap: '%s' and '%s'. "
                        + "Merged min: %s, merged max: %s",
                dep1, dep2, minimumVersion, maximumVersion));
        }

        // Recommended version. Check that it matches the inferred min and max.
        // If none of them do, then pick the min version.
        Optional<OrderableSlsVersion> recommendedVersion = Stream
                .of(dep1.getRecommendedVersion(), dep2.getRecommendedVersion())
                .filter(Objects::nonNull)
                .filter(SlsProductVersions::isOrderableVersion)
                .map(OrderableSlsVersion::valueOf)
                .filter(version -> VersionComparator.INSTANCE.compare(version, minimumVersion) >= 0
                        && satisfiesMaxVersion(maximumVersion, version))
                .max(VersionComparator.INSTANCE);

        RecommendedProductDependency result = new RecommendedProductDependency();
        result.setMinimumVersion(minimumVersion.toString());
        maximumVersion.map(Objects::toString).ifPresent(result::setMaximumVersion);
        recommendedVersion.map(Objects::toString).ifPresent(result::setRecommendedVersion);
        result.setProductGroup(dep1.getProductGroup());
        result.setProductName(dep1.getProductName());
        result.isValid();
        return result;
    }

    private static boolean satisfiesMaxVersion(Optional<MaximumVersion> maximumVersion, OrderableSlsVersion version) {
        // If maximumVersion is 1.5.x we should still accept e.g. 1.3.0 so we use '>= 0'
        // (comparison result is from the point of view of the matcher)
        return maximumVersion.map(maxVer -> maxVer.isSatisfiedBy(version)).orElse(true);
    }

}
