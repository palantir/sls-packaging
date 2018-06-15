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

import com.palantir.sls.versions.SlsVersionMatcher;
import java.util.Comparator;
import java.util.OptionalInt;

enum MatcherOrVersionComparator implements Comparator<MatcherOrVersion> {
    INSTANCE;

    private static final Comparator<OptionalInt> EMPTY_IS_GREATER =
            Comparator.comparingInt(num -> num.isPresent() ? num.getAsInt() : Integer.MAX_VALUE);

    private static final Comparator<SlsVersionMatcher> MATCHER_COMPARATOR = Comparator
            .comparing(SlsVersionMatcher::getMajorVersionNumber, EMPTY_IS_GREATER)
            .thenComparing(SlsVersionMatcher::getMinorVersionNumber, EMPTY_IS_GREATER)
            .thenComparing(SlsVersionMatcher::getPatchVersionNumber, EMPTY_IS_GREATER);

    @Override
    public int compare(MatcherOrVersion o1, MatcherOrVersion o2) {
        return o1.fold(
                thisVersion -> -o2.compareTo(thisVersion), // reverse comparison as it's from POV of o2
                thisMatcher -> o2.fold(
                        version -> thisMatcher.compare(version),
                        matcher -> MATCHER_COMPARATOR.compare(thisMatcher, matcher)));
    }
}
