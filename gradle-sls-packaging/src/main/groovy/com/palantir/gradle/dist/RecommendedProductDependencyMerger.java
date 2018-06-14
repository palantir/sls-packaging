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

import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.sls.versions.OrderableSlsVersion;
import com.palantir.sls.versions.SlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import com.palantir.sls.versions.VersionComparator;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

public final class RecommendedProductDependencyMerger {

    private static final Comparator<OptionalInt> EMPTY_IS_GREATER =
            Comparator.comparingInt(num -> num.isPresent() ? num.getAsInt() : Integer.MAX_VALUE);

    public static final Comparator<SlsVersionMatcher> MATCHER_COMPARATOR = Comparator
            .comparing(SlsVersionMatcher::getMajorVersionNumber, EMPTY_IS_GREATER)
            .thenComparing(SlsVersionMatcher::getMinorVersionNumber, EMPTY_IS_GREATER)
            .thenComparing(SlsVersionMatcher::getPatchVersionNumber, EMPTY_IS_GREATER);

    private RecommendedProductDependencyMerger() { }

    public static RecommendedProductDependency mergeRecommendedProductDependencies(
            RecommendedProductDependency dep1, RecommendedProductDependency dep2) {
        // Ensure they are valid
        Arrays.asList(dep1, dep2).forEach(RecommendedProductDependency::isValid);
        Preconditions.checkArgument(
                dep1.getProductGroup().equals(dep2.getProductGroup()),
                "Product groups differ",
                SafeArg.of("dep1ProductGroup", dep1.getProductGroup()),
                SafeArg.of("dep2ProductGroup", dep2.getProductGroup()));
        Preconditions.checkArgument(
                dep1.getProductName().equals(dep2.getProductName()),
                "Product names differ",
                SafeArg.of("dep1ProductName", dep1.getProductName()),
                SafeArg.of("dep2ProductName", dep2.getProductName()));

        VersionComparator comparator = VersionComparator.INSTANCE;
        OrderableSlsVersion minimumVersion = Collections.max(
                Arrays.asList(
                        OrderableSlsVersion.valueOf(dep1.getMinimumVersion()),
                        OrderableSlsVersion.valueOf(dep2.getMinimumVersion())),
                comparator);

        Optional<SlsVersionMatcher> maximumVersion = Stream
                .of(
                        SlsVersionMatcher.valueOf(dep1.getMaximumVersion()),
                        SlsVersionMatcher.valueOf(dep2.getMaximumVersion()))
                .filter(Objects::nonNull)
                .min(MATCHER_COMPARATOR);

        // Recommended version. Check that it matches the inferred min and max.
        // If none of them do, then pick the min version.
        SlsVersion recommendedVersion = Stream
                .of(
                        OrderableSlsVersion.valueOf(dep1.getRecommendedVersion()),
                        OrderableSlsVersion.valueOf(dep2.getRecommendedVersion()))
                .filter(version -> comparator.compare(version, minimumVersion) >= 0
                        // If maximumVersion is 1.5.x we should still accept e.g. 1.3.0 so we use '<= 0'
                        && maximumVersion.map(maxVer -> maxVer.compare(version) <= 0).orElse(true))
                .max(comparator)
                .orElse(minimumVersion);

        RecommendedProductDependency result = new RecommendedProductDependency();
        result.setMetaClass(dep1.getMetaClass());
        maximumVersion.ifPresent(maxVer -> result.setMaximumVersion(maxVer.toString()));
        result.setMinimumVersion(minimumVersion.toString());
        result.setRecommendedVersion(recommendedVersion.toString());
        result.setProductGroup(dep1.getProductGroup());
        // Verify validity
        result.isValid();
        return result;
    }

}
