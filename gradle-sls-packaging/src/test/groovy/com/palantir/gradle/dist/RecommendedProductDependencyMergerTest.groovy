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

import org.codehaus.groovy.runtime.InvokerHelper
import spock.lang.Specification

class RecommendedProductDependencyMergerTest extends Specification {
    def basicDep = new RecommendedProductDependency(
        productGroup: "group",
        productName: "name")

    def "picks larger minimum version and smaller maximum version"() {
        given:
        def dep1 = newRecommendation("2.0.0", "2.6.x", "2.2.0")
        def dep2 = newRecommendation("2.1.0", "2.x.x", "2.2.0")

        when:
        def merged = RecommendedProductDependencyMerger.merge(dep1, dep2)

        then:
        merged.minimumVersion == "2.1.0"
        merged.maximumVersion == "2.6.x"
        merged.recommendedVersion == "2.2.0"
    }

    def "non-orderable versions will not be picked"() {
        given:
        def dep1 = newRecommendation("2.1.0", "2.6.x", "2.3.0.dirty")
        def dep2 = newRecommendation("2.2.0.dirty", "2.x.x", "2.2.0")
        def dep3 = newRecommendation("2.2.0", "2.x.x", null)

        when:
        def merged1 = RecommendedProductDependencyMerger.merge(dep1, dep2)
        def merged2 = RecommendedProductDependencyMerger.merge(dep1, dep3)

        then:
        merged1.minimumVersion == "2.1.0"
        merged1.maximumVersion == "2.6.x"
        merged1.recommendedVersion == "2.2.0"

        merged2.minimumVersion == "2.2.0"
        merged2.maximumVersion == "2.6.x"
        merged2.recommendedVersion == null
    }

    def "fails if new min is greater than new max"() {
        given:
        def dep1 = newRecommendation("2.0.0", "2.6.x", "2.2.0")
        def dep2 = newRecommendation("2.7.0", "2.x.x", "2.8.0")

        when:
        def merged = RecommendedProductDependencyMerger.merge(dep1, dep2)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Could not merge recommended product dependencies as their version ranges do not overlap")
    }

    def "fails if no orderable minimum version could be found"() {
        given:
        def dep1 = newRecommendation("2.0.0.dirty", "2.6.x", "2.2.0")
        def dep2 = newRecommendation("2.7.0.dirty", "2.x.x", "2.8.0")

        when:
        RecommendedProductDependencyMerger.merge(dep1, dep2)

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Unable to determine minimum version")
    }

    def "fails if min == max"() {
        given:
        def dep1 = newRecommendation("2.5.0", "2.x.x", null)
        def dep2 = newRecommendation("2.1.0", "2.5.0", null)

        when:
        def merged = RecommendedProductDependencyMerger.merge(dep1, dep2)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("minimumVersion and maximumVersion must be different")
    }

    def "max and recommended can be optional"() {
        given:
        def dep1 = newRecommendation("2.1.0", null, null)
        def dep2 = newRecommendation("2.1.0", null, null)

        when:
        def merged = RecommendedProductDependencyMerger.merge(dep1, dep2)

        then:
        merged.minimumVersion == "2.1.0"
        merged.maximumVersion == null
        merged.recommendedVersion == null
    }

    def "orderable version gets picked over non-orderable version"() {
        given:
        def dep1 = newRecommendation("2.1.0", null, "2.6.0")
        def dep2 = newRecommendation("2.2.0.dirty", "2.8.x", null)

        when:
        def merged = RecommendedProductDependencyMerger.merge(dep1, dep2)

        then:
        merged.minimumVersion == "2.1.0"
        merged.maximumVersion == "2.8.x"
        merged.recommendedVersion == "2.6.0"
    }

    private RecommendedProductDependency newRecommendation(String min, String max, String recommended) {
        def rpd = basicDep.clone() as RecommendedProductDependency
        use InvokerHelper, {
            rpd.setProperties(minimumVersion: min, maximumVersion: max, recommendedVersion: recommended)
        }
        return rpd
    }
}
