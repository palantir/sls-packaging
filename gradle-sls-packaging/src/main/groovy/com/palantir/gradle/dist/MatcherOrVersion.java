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
import com.palantir.sls.versions.SlsVersionMatcher;
import com.palantir.sls.versions.VersionComparator;
import java.util.function.Function;

interface MatcherOrVersion extends Comparable<MatcherOrVersion> {
    <T> T fold(
            Function<? super OrderableSlsVersion, ? extends T> ifVersion,
            Function<? super SlsVersionMatcher, ? extends T> ifMatcher);

    /**
     * Convenience compareTo that compares us with a {@link OrderableSlsVersion}.
     */
    default int compareTo(OrderableSlsVersion version) {
        return fold(
                thisVersion -> VersionComparator.INSTANCE.compare(thisVersion, version),
                thisMatcher -> thisMatcher.compare(version));
    }

    @Override
    default int compareTo(MatcherOrVersion other) {
        return MatcherOrVersionComparator.INSTANCE.compare(this, other);
    }

    static MatcherOrVersion valueOf(String version) {
        return OrderableSlsVersion
                .safeValueOf(version)
                .<MatcherOrVersion>map(VersionCase::new)
                .orElseGet(() -> new MatcherCase(SlsVersionMatcher.valueOf(version)));
    }
}
