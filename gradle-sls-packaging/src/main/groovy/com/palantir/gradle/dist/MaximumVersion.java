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
import com.palantir.sls.versions.SlsVersionType;
import com.palantir.sls.versions.VersionComparator;
import java.util.Optional;

/**
 * A typed version of a maximum version as declared in {@link RecommendedProductDependency#maximumVersion}.
 * It can be represented by either:
 * <ul>
 *     <li>an {@link OrderableSlsVersion}, or</li>
 *     <li>an {@link SlsVersionMatcher}</li>
 * </ul>
 * Hence, this class encodes a functional either-type.
 * <p>
 * The dichotomy is necessary because {@link SlsVersionMatcher}, though it has some overlap with
 * {@link OrderableSlsVersion}, doesn't handle all the cases that the latter supports. For instance, it can't represent
 * non-release versions (see version types enum {@link SlsVersionType}).
 */
abstract class MaximumVersion implements Comparable<MaximumVersion> {

    abstract <T> T visit(MaximumVersionVisitor<T> visitor);

    /**
     * True if the {@link OrderableSlsVersion} is less than or equal to the maximum version encoded in this object.
     */
    final boolean isSatisfiedBy(OrderableSlsVersion version) {
        return visit(new MaximumVersionVisitor<Boolean>() {
            @Override
            public Boolean visitVersion(OrderableSlsVersion thisVersion) {
                return VersionComparator.INSTANCE.compare(thisVersion, version) >= 0;
            }

            @Override
            public Boolean visitMatcher(SlsVersionMatcher matcher) {
                // We're going for 'which is more restrictive as a max'
                // outcome of '0' means that version is accepted by matcher (not that they're equal)
                return matcher.compare(version) >= 0;
            }
        });
    }

    @Override
    public final int compareTo(MaximumVersion other) {
        return MaximumVersionComparator.INSTANCE.compare(this, other);
    }

    @Override
    public final String toString() {
        return visit(new MaximumVersionVisitor<String>() {
            @Override
            public String visitVersion(OrderableSlsVersion version) {
                return version.toString();
            }

            @Override
            public String visitMatcher(SlsVersionMatcher matcher) {
                return matcher.toString();
            }
        });
    }

    static MaximumVersion valueOf(String version) {
        Optional<OrderableSlsVersion> maybeOrderable = OrderableSlsVersion.safeValueOf(version);
        if (maybeOrderable.isPresent()) {
            return new VersionMaximumVersion(maybeOrderable.get());
        }

        Optional<SlsVersionMatcher> maybeMatcher = SlsVersionMatcher.safeValueOf(version);
        if (maybeMatcher.isPresent()) {
            return new MatcherMaximumVersion(maybeMatcher.get());
        }

        throw new SafeIllegalArgumentException(
                "Couldn't parse version as an OrderableSlsVersion or an SlsVersionMatcher",
                UnsafeArg.of("version", version));
    }

    /**
     * Matchers may contain wildcards, e.g. {@code 1.x.x}, and can only match {@link SlsVersionType#RELEASE} versions.
     */
    static final class MatcherMaximumVersion extends MaximumVersion {
        private final SlsVersionMatcher matcher;

        MatcherMaximumVersion(SlsVersionMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        <T> T visit(MaximumVersionVisitor<T> visitor) {
            return visitor.visitMatcher(matcher);
        }
    }

    static final class VersionMaximumVersion extends MaximumVersion {
        private final OrderableSlsVersion version;

        VersionMaximumVersion(OrderableSlsVersion version) {
            this.version = version;
        }

        @Override
        <T> T visit(MaximumVersionVisitor<T> visitor) {
            return visitor.visitVersion(version);
        }
    }
}
