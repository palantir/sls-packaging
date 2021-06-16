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

package com.palantir.gradle.dist.pdeps

import com.google.common.collect.ImmutableSetMultimap
import com.palantir.gradle.dist.ProductDependency
import com.palantir.gradle.dist.ProductId
import nebula.test.ProjectSpec

class ResolveProductDependenciesProjectSpec extends ProjectSpec {
    private static final ProductDependency PDEP = new ProductDependency(
            "group", "name", "1.0.0", "1.x.x", "1.0.0");
    private static final ProductDependency PDEP_2 = new ProductDependency(
            "group", "name", "1.2.0", "1.x.x", "1.2.0");

    private static final ProductId PRODUCT_ID = ProductId.of(PDEP);

    private ResolveProductDependenciesTask task;

    void setup() {
        project.version = "1.0.0"
        task = project.tasks.create("m", ResolveProductDependenciesTask)

        task.serviceGroup.set("com.palantir.test")
        task.serviceName.set("test")
    }

    def 'merges declared product dependencies'() {
        when:
        def result = task.computeDependencies(List.of(PDEP, PDEP_2), ImmutableSetMultimap.of())

        then:
        result.get(PRODUCT_ID).minimumVersion == PDEP_2.minimumVersion
    }

    def 'merges declared productDependencies with discovered dependencies'() {
        when:
        def result = task.computeDependencies(List.of(PDEP_2), ImmutableSetMultimap.of(PRODUCT_ID, PDEP))

        then:
        result.get(PRODUCT_ID).minimumVersion == PDEP_2.minimumVersion
    }

    def 'throws if declared dependency is also ignored'() {
        when:
        task.ignoredProductIds.add(PRODUCT_ID)
        def result =  task.computeDependencies(List.of(PDEP), ImmutableSetMultimap.of())

        then:
        def e = thrown IllegalArgumentException
        e.message.contains("Encountered product dependency declaration that was also ignored")
    }

    def 'throws if declared dependency is also optional'() {
        when:
        task.optionalProductIds.add(PRODUCT_ID)
        task.computeDependencies(List.of(PDEP), ImmutableSetMultimap.of())

        then:
        def e = thrown IllegalArgumentException
        e.message.contains("Encountered product dependency declaration that was also declared as optional")
    }

    def "throws on declared self-dependency"() {
        when:
        task.serviceGroup.set("group")
        task.serviceName.set("name")
        task.computeDependencies(List.of(PDEP), ImmutableSetMultimap.of())

        then:
        def e = thrown IllegalArgumentException
        e.message.contains("Invalid for product to declare an explicit dependency on itself")
    }

    def "Ignores discovered self-dependency"() {
        when:
        task.serviceGroup.set("group")
        task.serviceName.set("name")
        def result = task.computeDependencies(List.of(), ImmutableSetMultimap.of(PRODUCT_ID, PDEP))

        then:
        result.isEmpty()
    }

    def 'ignores discovered product dependency'() {
        when:
        task.ignoredProductIds.add(PRODUCT_ID)
        def result = task.computeDependencies(List.of(), ImmutableSetMultimap.of(PRODUCT_ID, PDEP))

        then:
        result.isEmpty()
    }

    def 'mark as optional product dependencies'() {
        when:
        task.optionalProductIds.add(PRODUCT_ID)
        def result = task.computeDependencies(List.of(), ImmutableSetMultimap.of(PRODUCT_ID, PDEP))

        then:
        result.get(PRODUCT_ID).optional
    }

    def "Merges discovered dependencies"() {
        when:
        def result = task.computeDependencies(List.of(), ImmutableSetMultimap.of(PRODUCT_ID, PDEP, PRODUCT_ID, PDEP_2))

        then:
        result.get(PRODUCT_ID).minimumVersion == PDEP_2.minimumVersion
    }
}
