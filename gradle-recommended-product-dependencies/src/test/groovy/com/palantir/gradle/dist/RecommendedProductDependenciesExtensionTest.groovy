/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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


import nebula.test.ProjectSpec

class RecommendedProductDependenciesExtensionTest extends ProjectSpec {

    def 'does not allow you to add an optional dependency'() {
        RecommendedProductDependenciesExtension ext = new RecommendedProductDependenciesExtension(getProject());

        when:
        ext.productDependency({ ->
            productGroup = 'group'
            productName = 'name'
            minimumVersion = '1.0.0'
            maximumVersion = '1.x.x'
            recommendedVersion = '1.2.3'
            optional = true
        });
        ext.recommendedProductDependenciesProvider.get()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains 'Optional dependencies are not supported'
    }
}
