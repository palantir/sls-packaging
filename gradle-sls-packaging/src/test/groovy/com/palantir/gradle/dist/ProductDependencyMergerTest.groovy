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


import spock.lang.Specification

class ProductDependencyMergerTest extends Specification {
    def "picks larger minimum version and smaller maximum version"() {
        given:
        def dep1 = newRecommendation("2.0.0", "2.6.x", "2.2.0")
        def dep2 = newRecommendation("2.1.0", "2.x.x", "2.2.0")

        when:
        def merged = ProductDependencyMerger.merge(dep1, dep2)

        then:
        merged.minimumVersion == "2.1.0"
        merged.maximumVersion == "2.6.x"
        merged.recommendedVersion == "2.2.0"
    }

    def "fails if new min is greater than new max"() {
        given:
        def dep1 = newRecommendation("2.0.0", "2.6.x", "2.2.0")
        def dep2 = newRecommendation("2.7.0", "2.x.x", "2.8.0")

        when:
        def merged = ProductDependencyMerger.merge(dep1, dep2)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Could not merge recommended product dependencies as their version ranges do not overlap")
    }

    def "fails if min == max"() {
        given:
        def dep1 = newRecommendation("2.5.0", "2.x.x")
        def dep2 = newRecommendation("2.1.0", "2.5.0")

        when:
        def merged = ProductDependencyMerger.merge(dep1, dep2)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("minimumVersion and maximumVersion must be different")
    }

    def "merges optional and non-optional"() {
        given:
        def dep1 = newRecommendation("2.0.0", "2.x.x")
        def dep2 = new ProductDependency("group", "name", "2.0.0", "2.x.x", null, true)

        when:
        def merged = ProductDependencyMerger.merge(dep1, dep2)
        def merged2 = ProductDependencyMerger.merge(dep2, dep2)

        then:
        !merged.optional
        merged2.optional
    }

    private ProductDependency newRecommendation(String min, String max, String recommended = null) {
        return new ProductDependency("group", "name", min, max, recommended)
    }
}
