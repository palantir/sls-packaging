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

import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator

class ProductDependencyIntrospectionPluginIntegrationSpec extends IntegrationSpec {
    def "adds product dependency constraints to configuration"() {
        buildFile << 'apply plugin: com.palantir.gradle.dist.ProductDependencyIntrospectionPlugin'

        file("product-dependencies.lock").text = '''\
            # Run ./gradlew --write-locks to regenerate this file
            com.palantir.product:test (1.0.0, 1.x.x)
        '''.stripIndent()
        def mavenRepo = generateMavenRepo('com.palantir.product:test:1.0.0')
        buildFile << """
            repositories {
                maven { url "file://${mavenRepo.absolutePath}" }
            }
            configurations {
                foo {
                    extendsFrom configurations.productDependencies
                }
            }
            dependencies {
                foo 'com.palantir.product:test'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'foo')

        then:
        result.standardOutput.contains("com.palantir.product:test -> 1.0.0")
    }

    def "merges product dependency constraints from different projects"() {
        file("a/product-dependencies.lock").text = '''\
            # Run ./gradlew --write-locks to regenerate this file
            com.palantir.product:test (1.0.0, 1.x.x)
        '''.stripIndent()

        file("b/product-dependencies.lock").text = '''\
            # Run ./gradlew --write-locks to regenerate this file
            com.palantir.product:test (1.2.0, 1.6.x)
        '''.stripIndent()

        addSubproject('a', 'apply plugin: com.palantir.gradle.dist.ProductDependencyIntrospectionPlugin')
        addSubproject('b', 'apply plugin: com.palantir.gradle.dist.ProductDependencyIntrospectionPlugin')

        def mavenRepo = generateMavenRepo(
                'com.palantir.product:test:1.0.0',
                'com.palantir.product:test:1.2.0')

        buildFile << """
            repositories {
                maven { url "file://${mavenRepo.absolutePath}" }
            }
            configurations {
                foo
            }
            dependencies {
                foo project(path: ':a', configuration: 'productDependencies')
                foo project(path: ':b', configuration: 'productDependencies')
                foo 'com.palantir.product:test'
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('dependencies', '--configuration', 'foo')

        then:
        result.standardOutput.contains("com.palantir.product:test -> 1.2.0")
    }

    File generateMavenRepo(String... graph) {
        DependencyGraph dependencyGraph = new DependencyGraph(graph)
        GradleDependencyGenerator generator = new GradleDependencyGenerator(dependencyGraph)
        return generator.generateTestMavenRepo()
    }
}
