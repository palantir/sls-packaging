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

import com.palantir.gradle.dist.tasks.CreateManifestTask

class BaseDistributionPluginIntegrationSpec extends GradleIntegrationSpec {
    def setup() {
        buildFile << '''
        plugins {
            id 'com.palantir.sls-java-service-distribution'
        }
        
        task demo {
            doLast { println "minimum-version=" + getMinimumProductVersion('com.palantir.product:test') }
        }
        '''.stripIndent()
    }

    def "get version from lock file"() {
        file(CreateManifestTask.PRODUCT_DEPENDENCIES_LOCK) << ProductDependencyLockFile.asString(
                [new ProductDependency("com.palantir.product", "test", "1.0.0", "1.x.x", null)],
                [] as Set<ProductId>,
                "1.0.0"
        )

        when:
        def result = runTasks("demo")

        then:
        result.output.contains("minimum-version=1.0.0")
    }

    def "fails if lock file does not exist"() {
        when:
        def result = runTasksAndFail("demo")

        then:
        result.output.contains("product-dependencies.lock does not exist. Run ./gradlew --write-locks to generate it.")
    }

    def "fails if dependency does not exist in lock file"() {
        file(CreateManifestTask.PRODUCT_DEPENDENCIES_LOCK) << ProductDependencyLockFile.asString(
                [new ProductDependency("com.palantir.other", "test", "1.0.0", "1.x.x", null)],
                [] as Set<ProductId>,
                "1.0.0"
        )

        when:
        def result = runTasksAndFail("demo")

        then:
        result.output.contains("Unable to find product dependency for 'com.palantir.product:test'")
    }
}
