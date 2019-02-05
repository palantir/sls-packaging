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

import com.google.common.base.Preconditions;
import com.palantir.sls.versions.OrderableSlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import com.palantir.sls.versions.VersionComparator;
import java.util.stream.Stream;

public final class ProductDependencyMerger {
    private ProductDependencyMerger() { }

    public static ProductDependency merge(ProductDependency dep1, ProductDependency dep2) {
        // Ensure they are valid
        if (!dep1.getProductGroup().equals(dep2.getProductGroup())) {
            throw new IllegalArgumentException(String.format("Product groups differ: '%s' and '%s'",
                dep1.getProductGroup(), dep2.getProductGroup()));
        }
        if (!dep1.getProductName().equals(dep2.getProductName())) {
            throw new IllegalArgumentException(String.format("Product names differ: '%s' and '%s'",
                dep1.getProductName(), dep2.getProductName()));
        }

        OrderableSlsVersion minimumVersion = Stream.of(dep1.getMinimumVersion(), dep2.getMinimumVersion())
                .flatMap(version -> OrderableSlsVersion.safeValueOf(version).map(Stream::of).orElse(Stream.empty()))
                .max(VersionComparator.INSTANCE)
                .orElseThrow(() -> new RuntimeException("Unable to determine minimum version"));

        SlsVersionMatcher maximumVersion = Stream
                .of(dep1.getMaximumVersion(), dep2.getMaximumVersion())
                .map(SlsVersionMatcher::valueOf)
                .min(SlsVersionMatcher.MATCHER_COMPARATOR)
                .orElseThrow(() -> new RuntimeException("Impossible"));

        // Sanity check: min has to be <= max
        Preconditions.checkArgument(
                satisfiesMaxVersion(maximumVersion, minimumVersion),
                "Could not merge recommended product dependencies as their version ranges do not overlap: '%s' "
                        + "and '%s'. Merged min: %s, merged max: %s",
                dep1, dep2, minimumVersion, maximumVersion);

        // Recommended version. Check that it matches the inferred min and max.
        // If none of them do, then pick the min version.
        OrderableSlsVersion recommendedVersion = Stream
                .of(dep1.getRecommendedVersion(), dep2.getRecommendedVersion())
                .flatMap(version -> OrderableSlsVersion.safeValueOf(version).map(Stream::of).orElse(Stream.empty()))
                .filter(version -> VersionComparator.INSTANCE.compare(version, minimumVersion) >= 0
                        && satisfiesMaxVersion(maximumVersion, version))
                .max(VersionComparator.INSTANCE)
                .orElse(minimumVersion);

        ProductDependency result = new ProductDependency();
        result.setMinimumVersion(minimumVersion.toString());
        result.setMaximumVersion(maximumVersion.toString());
        result.setRecommendedVersion(recommendedVersion.toString());
        result.setProductGroup(dep1.getProductGroup());
        result.setProductName(dep1.getProductName());
        result.isValid();
        return result;
    }

    private static boolean satisfiesMaxVersion(SlsVersionMatcher maximumVersion, OrderableSlsVersion version) {
        // If maximumVersion is 1.5.x we should still accept e.g. 1.3.0 so we use '>= 0'
        // (comparison result is from the point of view of the matcher)
        return maximumVersion.compare(version) >= 0;
    }

}
