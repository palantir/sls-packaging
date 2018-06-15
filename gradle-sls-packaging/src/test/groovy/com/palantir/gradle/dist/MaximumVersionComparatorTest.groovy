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

package com.palantir.gradle.dist

import com.palantir.sls.versions.OrderableSlsVersion
import com.palantir.sls.versions.SlsVersionMatcher
import spock.lang.Specification

class MaximumVersionComparatorTest extends Specification {
    def matcherComparatorTest() {
        given:
        def m1 = SlsVersionMatcher.valueOf("2.6.x")
        def m2 = SlsVersionMatcher.valueOf("2.x.x")
        expect:
        MaximumVersionComparator.MATCHER_COMPARATOR.compare(m1, m2) < 0
    }

    def comparesMatchers() {
        given:
        def m1 = MaximumVersion.valueOf("2.6.x")
        def m2 = MaximumVersion.valueOf("2.x.x")
        expect:
        MaximumVersionComparator.INSTANCE.compare(m1, m2) < 0
        MaximumVersionComparator.INSTANCE.compare(m2, m1) > 0
    }

    def comparesVersions() {
        given:
        def m1 = MaximumVersion.valueOf("2.6.2")
        def m2 = MaximumVersion.valueOf("2.6.9")
        expect:
        MaximumVersionComparator.INSTANCE.compare(m1, m2) < 0
        MaximumVersionComparator.INSTANCE.compare(m2, m1) > 0
        isVersion(m1)
        isVersion(m2)
    }

    def comparesMixed() {
        given:
        def m1 = MaximumVersion.valueOf("2.6.x")
        def m2 = MaximumVersion.valueOf("2.6.9")
        expect:
        MaximumVersionComparator.INSTANCE.compare(m1, m2) > 0
        MaximumVersionComparator.INSTANCE.compare(m2, m1) < 0
        !isVersion(m1)
        isVersion(m2)
    }

    def isVersion(MaximumVersion matcherOrVersion) {
        return matcherOrVersion.visit(new MaximumVersionVisitor<Boolean>() {
            @Override
            Boolean visitVersion(OrderableSlsVersion version) {
                return true
            }

            @Override
            Boolean visitMatcher(SlsVersionMatcher matcher) {
                return false
            }
        })
    }
}
