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
import com.google.common.collect.Streams;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.sls.versions.OrderableSlsVersion;
import com.palantir.sls.versions.SlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import com.palantir.sls.versions.VersionComparator;
import java.util.Objects;
import java.util.Optional;
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

        // This could be empty if both of the versions are dirty
        Optional<OrderableSlsVersion> minimumVersionOrderable = Stream.of(dep1.parseMinimum(), dep2.parseMinimum())
                .flatMap(Streams::stream)
                .max(VersionComparator.INSTANCE);

        SlsVersion minimumVersion;
        // If it's dirty or otherwise non-orderable, try to see if they're the same version and allow that.
        if (Objects.equals(dep1.getMinimumVersion(), dep2.getMinimumVersion())) {
            minimumVersion = SlsVersion.valueOf(dep1.getMinimumVersion());
        } else {
            minimumVersion = minimumVersionOrderable.orElseThrow(() -> new SafeRuntimeException(
                    "Could not determine minimum version among two non-orderable minimum versions",
                    SafeArg.of("dep1", dep1),
                    SafeArg.of("dep2", dep2)));
        }

        SlsVersionMatcher maximumVersion = Stream
                .of(dep1.parseMaximum(), dep2.parseMaximum())
                .min(SlsVersionMatcher.MATCHER_COMPARATOR)
                .orElseThrow(() -> new RuntimeException("Impossible"));

        // Sanity check: min has to be <= max
        Preconditions.checkArgument(
                minimumVersionOrderable.map(mv -> satisfiesMaxVersion(maximumVersion, mv)).orElse(true),
                "Could not merge recommended product dependencies as their version ranges do not overlap",
                SafeArg.of("dep1", dep1),
                SafeArg.of("dep2", dep2),
                SafeArg.of("mergedMinimum", minimumVersionOrderable),
                SafeArg.of("mergedMaximum", maximumVersion));

        // Recommended version. Check that it matches the inferred min and max.
        Optional<OrderableSlsVersion> recommendedVersion = Stream
                .of(dep1.parseRecommended(), dep2.parseRecommended())
                .flatMap(Streams::stream)
                .filter(version -> minimumVersionOrderable
                        .map(mv -> VersionComparator.INSTANCE.compare(version, mv) >= 0).orElse(true))
                .filter(version -> satisfiesMaxVersion(maximumVersion, version))
                .max(VersionComparator.INSTANCE);

        ProductDependency result = new ProductDependency();
        result.setMinimumVersion(minimumVersion.toString());
        result.setMaximumVersion(maximumVersion.toString());
        recommendedVersion.map(Objects::toString).ifPresent(result::setRecommendedVersion);
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
