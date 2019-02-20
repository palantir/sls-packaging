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

import com.google.common.collect.Iterables
import nebula.test.ProjectSpec
import org.gradle.api.GradleException

class ProductDependencyIntrospectionPluginTest extends ProjectSpec {
    def setup() {
        project.pluginManager.apply(ProductDependencyIntrospectionPlugin)
    }

    def "get version from lock file"() {
        project.file("product-dependencies.lock").text = '''\
        # Run ./gradlew --write-locks to regenerate this file
        com.palantir.product:test (1.0.0, 1.x.x)
        '''.stripIndent()

        when:
        def result = project.ext.getMinimumProductVersion("com.palantir.product:test")

        then:
        result == "1.0.0"
    }

    def "resolves project versions into concrete version"() {
        project.version = "1.1.0"
        project.file("product-dependencies.lock").text = '''\
        # Run ./gradlew --write-locks to regenerate this file
        com.palantir.product:test ($projectVersion, 1.x.x)
        '''.stripIndent()

        when:
        def result = project.ext.getMinimumProductVersion("com.palantir.product:test")

        then:
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
        project.file("product-dependencies.lock").text = '''\
        # Run ./gradlew --write-locks to regenerate this file
        com.palantir.product:test (1.0.0, 1.x.x)
        '''.stripIndent()

        when:
        project.ext.getMinimumProductVersion("com.palantir.other:test")

        then:
        GradleException exception = thrown()
        exception.message.contains("Unable to find product dependency for 'com.palantir.other:test'")
    }

    def "adds product dependency constraints to configuration"() {
        project.file("product-dependencies.lock").text = '''\
        # Run ./gradlew --write-locks to regenerate this file
        com.palantir.product:test (1.0.0, 1.x.x)
        '''.stripIndent()
        project.with {
            configurations {
                foo
            }
            dependencies {
                foo configurations.productDependencies
                foo 'com.palantir.product:test'
            }
        }

        project.evaluate()

        def result = project.configurations.foo.incoming.resolutionResult
        expect:
        def component = Iterables.getOnlyElement(result.allComponents - result.root)
        component.id.toString() == 'com.palantir.product:test'
        component.moduleVersion.version == '1.0.0'
    }
}
