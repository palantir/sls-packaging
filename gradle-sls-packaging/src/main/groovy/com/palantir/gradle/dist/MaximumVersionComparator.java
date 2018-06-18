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
import java.util.Comparator;

enum MaximumVersionComparator implements Comparator<MaximumVersion> {
    INSTANCE;

    @Override
    public int compare(MaximumVersion o1, MaximumVersion o2) {
        return o1.visit(new MaximumVersionVisitor<Integer>() {
            @Override
            public Integer visitVersion(OrderableSlsVersion version) {
                // o1 is smaller if it satisfies the max constraint of o2
                return o2.isSatisfiedBy(version) ? -1 : 1;
            }

            @Override
            public Integer visitMatcher(SlsVersionMatcher thisMatcher) {
                return o2.visit(new MaximumVersionVisitor<Integer>() {
                    @Override
                    public Integer visitVersion(OrderableSlsVersion version) {
                        // We're going for 'which is more restrictive as a max'
                        // outcome of 0 means that version is accepted by matcher
                        return thisMatcher.compare(version) >= 0 ? 1 : -1;
                    }

                    @Override
                    public Integer visitMatcher(SlsVersionMatcher matcher) {
                        return SlsVersionMatcher.MATCHER_COMPARATOR.compare(thisMatcher, matcher);
                    }
                });
            }
        });
    }
}
