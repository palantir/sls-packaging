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
        def merged = RecommendedProductDependencyMerger.mergeRecommendedProductDependencies(dep1, dep2)

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
        def merged = RecommendedProductDependencyMerger.mergeRecommendedProductDependencies(dep1, dep2)

        then:
        thrown(IllegalArgumentException)
    }

    private RecommendedProductDependency newRecommendation(String min, String max, String recommended) {
        def rpd = basicDep.clone() as RecommendedProductDependency
        use InvokerHelper, {
            rpd.setProperties(minimumVersion: min, maximumVersion: max, recommendedVersion: recommended)
        }
        return rpd
    }
}
