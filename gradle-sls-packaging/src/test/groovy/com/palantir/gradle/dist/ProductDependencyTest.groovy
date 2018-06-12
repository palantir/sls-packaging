/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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

class ProductDependencyTest extends Specification {
    def 'max version may be matcher'() {
        when:
        new ProductDependency("", "", "1.2.3", "1.2.x", "1.2.3").isValid()

        then:
        true
    }

    def 'min version must not be matcher'() {
        when:
        new ProductDependency("", "", "1.2.x", "1.2.x", "1.2.3").isValid()

        then:
        thrown(IllegalArgumentException)
    }

    def 'min version must not be null'() {
        when:
        new ProductDependency("", "", null, "1.2.x", "1.2.3").isValid()

        then:
        thrown(IllegalArgumentException)
    }

    def 'recommended version must not be matcher'() {
        when:
        new ProductDependency("", "", "1.2.3", "1.2.x", "1.2.x").isValid()

        then:
        thrown(IllegalArgumentException)
    }

    def 'non-deafult maximumVersion'() {
        when:
        def dep = new ProductDependency("", "", "1.2.3", "2.x.x", "1.2.4")

        then:
        dep.maximumVersion == "2.x.x"
    }

    def 'minimumVersion and maximumVersion must not be equal' () {
        when:
        new ProductDependency("", "", "1.2.3", "1.2.3", null).isValid()

        then:
        thrown(IllegalArgumentException)
    }
}
