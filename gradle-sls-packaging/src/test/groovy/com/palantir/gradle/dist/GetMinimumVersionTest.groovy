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


import nebula.test.ProjectSpec
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class GetMinimumVersionTest extends ProjectSpec {
    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
        GetMinimumProductVersion.createGetMinimumProductVersion(project)
    }

    def "get version from lock file"() {
        project.file(ProductDependencyLockFile.LOCK_FILE) << ProductDependencyLockFile.asString(
                [new ProductDependency("com.palantir.product", "test", "1.0.0", "1.x.x", null)],
                [] as Set<ProductId>,
                "1.0.0"
        )

        when:
        def result = project.ext.getMinimumProductVersion("com.palantir.product:test")

        then:
        result == "1.0.0"
    }

    def "resolves project versions"() {
        def lockFile = project.file(ProductDependencyLockFile.LOCK_FILE)
        project.version = "1.0.0"
        lockFile << ProductDependencyLockFile.asString(
                [new ProductDependency("com.palantir.product", "test", "1.0.0", "1.x.x", null)],
                [new ProductId("com.palantir.product", "test")] as Set<ProductId>,
                "1.0.0"
        )

        when:
        def result = project.ext.getMinimumProductVersion("com.palantir.product:test")

        then:
        lockFile.text == '''\
        # Run ./gradlew --write-locks to regenerate this file
        com.palantir.product:test ($projectVersion, 1.x.x)
        '''.stripIndent()
        result == project.version
    }

    def "fails if lock file does not exist"() {
        when:
        project.ext.getMinimumProductVersion("com.palantir.product:test")

        then:
        IllegalStateException exception = thrown()
        exception.message.contains("product-dependencies.lock does not exist. Run ./gradlew --write-locks to generate it.")
    }

    def "fails if dependency does not exist in lock file"() {
        project.file(ProductDependencyLockFile.LOCK_FILE) << ProductDependencyLockFile.asString(
                [new ProductDependency("com.palantir.other", "test", "1.0.0", "1.x.x", null)],
                [] as Set<ProductId>,
                "1.0.0"
        )

        when:
        project.ext.getMinimumProductVersion("com.palantir.product:test")

        then:
        GradleException exception = thrown()
        exception.message.contains("Unable to find product dependency for 'com.palantir.product:test'")
    }
}
