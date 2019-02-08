/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

class ProductDependencyLockFileTest extends Specification {

    def 'serialize'() {
        when:
        List<ProductDependency> sample = [
                new ProductDependency("com.palantir.product", "foo", "1.20.0", "1.x.x", null),
                new ProductDependency("com.palantir.other", "bar", "0.2.0", "0.x.x", null)
        ]

        then:
        ProductDependencyLockFile.asString(sample) == """
        # Run ./gradlew --write-locks to regenerate this file
        com.palantir.product:foo (1.20.0, 1.x.x)
        com.palantir.other:bar (0.2.0, 0.x.x)
        """.stripIndent().trim()
    }
}
