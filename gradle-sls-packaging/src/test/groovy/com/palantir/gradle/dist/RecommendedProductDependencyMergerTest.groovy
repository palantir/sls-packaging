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
        def dep1 = newRecommendation(
            minimumVersion: "2.0.0",
            maximumVersion: "2.6.x",
            recommendedVersion: "2.2.0")

        def dep2 = newRecommendation(
            minimumVersion: "2.1.0",
            maximumVersion: "2.x.x",
            recommendedVersion: "2.2.0")

        when:
        def merged = RecommendedProductDependencyMerger.mergeRecommendedProductDependencies(dep1, dep2)

        then:
        merged.minimumVersion == "2.1.0"
        merged.maximumVersion == "2.6.x"
        merged.recommendedVersion == "2.2.0"
    }

    private RecommendedProductDependency newRecommendation(Map<String, String> values) {
        def rpd = basicDep.clone() as RecommendedProductDependency
        use InvokerHelper, {
            rpd.setProperties(values)
        }
        return rpd
    }
}
