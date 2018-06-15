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

import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.sls.versions.OrderableSlsVersion;
import com.palantir.sls.versions.SlsVersionMatcher;
import com.palantir.sls.versions.VersionComparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

abstract class MaximumVersion implements Comparable<MaximumVersion> {
    public abstract <T> T fold(
            Function<? super OrderableSlsVersion, ? extends T> ifVersion,
            Function<? super SlsVersionMatcher, ? extends T> ifMatcher);

    /**
     * True if the {@link OrderableSlsVersion} is less than or equal to the maximum version encoded in this object.
     */
    final boolean isSatisfiedBy(OrderableSlsVersion version) {
        return fold(
                thisVersion -> VersionComparator.INSTANCE.compare(thisVersion, version) >= 0,
                // We're going for 'which is more restrictive as a max'
                // outcome of '0' means that version is accepted by matcher (not that they're equal)
                thisMatcher -> thisMatcher.compare(version) >= 0);
    }

    @Override
    public final int compareTo(MaximumVersion other) {
        return MaximumVersionComparator.INSTANCE.compare(this, other);
    }

    @Override
    public final String toString() {
        return fold(Objects::toString, Objects::toString);
    }

    static MaximumVersion valueOf(String version) {
        return Stream
                .concat(
                        Stream.generate(() -> OrderableSlsVersion.safeValueOf(version).map(VersionMaximumVersion::new)),
                        Stream.generate(() -> SlsVersionMatcher.safeValueOf(version).map(MatcherMaximumVersion::new)))
                .flatMap(MaximumVersion::optionalToStream)
                .findFirst()
                .orElseThrow(() -> new SafeIllegalArgumentException(
                        "Couldn't parse version as an OrderableSlsVersion or an SlsVersionMatcher",
                        UnsafeArg.of("version", version)));
    }

    private static <T> Stream<T> optionalToStream(Optional<T> opt) {
        return opt.map(Stream::of).orElse(Stream.empty());
    }
}
