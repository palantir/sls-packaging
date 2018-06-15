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

import static com.palantir.logsafe.Preconditions.checkArgument;

import com.palantir.logsafe.SafeArg;
import com.palantir.sls.versions.OrderableSlsVersion;
import com.palantir.sls.versions.VersionComparator;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class RecommendedProductDependencyMerger {
    private RecommendedProductDependencyMerger() { }

    public static RecommendedProductDependency mergeRecommendedProductDependencies(
            RecommendedProductDependency dep1, RecommendedProductDependency dep2) {
        // Ensure they are valid
        Arrays.asList(dep1, dep2).forEach(RecommendedProductDependency::isValid);
        checkArgument(
                dep1.getProductGroup().equals(dep2.getProductGroup()),
                "Product groups differ",
                SafeArg.of("dep1ProductGroup", dep1.getProductGroup()),
                SafeArg.of("dep2ProductGroup", dep2.getProductGroup()));
        checkArgument(
                dep1.getProductName().equals(dep2.getProductName()),
                "Product names differ",
                SafeArg.of("dep1ProductName", dep1.getProductName()),
                SafeArg.of("dep2ProductName", dep2.getProductName()));

        VersionComparator versionComparator = VersionComparator.INSTANCE;
        OrderableSlsVersion minimumVersion = Stream
                .of(
                        OrderableSlsVersion.valueOf(dep1.getMinimumVersion()),
                        OrderableSlsVersion.valueOf(dep2.getMinimumVersion()))
                .max(versionComparator)
                .orElseThrow(() -> new RuntimeException("Impossible"));

        MaximumVersionComparator maximumVersionComparator = MaximumVersionComparator.INSTANCE;
        Optional<MaximumVersion> maximumVersion = Stream
                .of(dep1.getMaximumVersion(), dep2.getMaximumVersion())
                .filter(Objects::nonNull)
                .map(MaximumVersion::valueOf)
                .min(maximumVersionComparator);

        // Sanity check: min has to be <= max
        checkArgument(
                satisfiesMaxVersion(maximumVersion, minimumVersion),
                "Inferred minimum version does not match inferred maximum version constraint",
                SafeArg.of("minimumVersion", minimumVersion),
                SafeArg.of("maximumVersion", maximumVersion));

        // Recommended version. Check that it matches the inferred min and max.
        // If none of them do, then pick the min version.
        Optional<OrderableSlsVersion> recommendedVersion = Stream
                .of(dep1.getRecommendedVersion(), dep2.getRecommendedVersion())
                .filter(Objects::nonNull)
                .map(OrderableSlsVersion::valueOf)
                .filter(version -> versionComparator.compare(version, minimumVersion) >= 0
                        && satisfiesMaxVersion(maximumVersion, version))
                .max(versionComparator);

        RecommendedProductDependency result = new RecommendedProductDependency();
        result.setMinimumVersion(minimumVersion.toString());
        maximumVersion.map(Objects::toString).ifPresent(result::setMaximumVersion);
        recommendedVersion.map(Objects::toString).ifPresent(result::setRecommendedVersion);
        result.setProductGroup(dep1.getProductGroup());
        result.setProductName(dep1.getProductName());
        // Verify validity
        result.isValid();
        return result;
    }

    private static boolean satisfiesMaxVersion(Optional<MaximumVersion> maximumVersion, OrderableSlsVersion version) {
        // If maximumVersion is 1.5.x we should still accept e.g. 1.3.0 so we use '>= 0'
        // (comparison result is from the point of view of the matcher)
        return maximumVersion.map(maxVer -> maxVer.isSatisfiedBy(version)).orElse(true);
    }

}
