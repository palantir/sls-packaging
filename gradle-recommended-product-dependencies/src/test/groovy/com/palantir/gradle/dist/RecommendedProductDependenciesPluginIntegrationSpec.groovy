/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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
import java.util.zip.ZipFile
import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator

class RecommendedProductDependenciesPluginIntegrationSpec extends IntegrationSpec {
    def setup() {
        buildFile << """
        plugins {
            id 'com.palantir.consistent-versions' version '1.28.0' apply false
        }
        apply plugin: 'java'
        apply plugin: 'com.palantir.recommended-product-dependencies'
        """.stripIndent()

    }

    def "Manifest includes recommended product dependencies"() {
        buildFile << """
        recommendedProductDependencies {
            productDependency {
                productGroup = 'group'
                productName = 'name'
                minimumVersion = '1.0.0'
                maximumVersion = '1.x.x'
                recommendedVersion = '1.2.3'
            }
        }
        """.stripIndent()

        when:
        runTasksSuccessfully(':jar')

        then:
        fileExists("build/libs/${moduleName}.jar")

        def dep = Iterables.getOnlyElement(
                readRecommendedProductDeps(file("build/libs/${moduleName}.jar")).recommendedProductDependencies())
        dep.productGroup == "group"
        dep.productName == "name"
        dep.minimumVersion == "1.0.0"
        dep.maximumVersion == "1.x.x"
        dep.recommendedVersion == "1.2.3"
    }

    def 'Jar includes recommended product dependencies'() {
        buildFile << """
            recommendedProductDependencies {
                productDependency {
                    productGroup = 'group'
                    productName = 'name'
                    minimumVersion = '1.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = '1.2.3'
                }
            }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully(':jar')

        then:
        result.wasExecuted("compileRecommendedProductDependencies")
        fileExists("build/libs/${moduleName}.jar")

        def dep = Iterables.getOnlyElement(
                readRecommendedProductDeps(file("build/libs/${moduleName}.jar")).recommendedProductDependencies())
        dep.productGroup == "group"
        dep.productName == "name"
        dep.minimumVersion == "1.0.0"
        dep.maximumVersion == "1.x.x"
        dep.recommendedVersion == "1.2.3"
    }

    def "Works with consistent-versions"() {
        def repo = generateMavenRepo('group:name:1.0.0')
        buildFile << """
        apply plugin: 'com.palantir.consistent-versions'   
        apply plugin: 'com.palantir.recommended-product-dependencies'
        
        
        repositories {
            ${repo.mavenRepositoryBlock}
        }
        
        dependencies {
            // just so it becomes available to gradle-consistent-versions' getVersion
            implementation 'group:name:1.0.0'
        }

        recommendedProductDependencies {
            productDependency {
                productGroup = 'group'
                productName = 'name'
                minimumVersion = getVersion('group:name')
                maximumVersion = '1.x.x'
            }
        }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully( '--write-locks', ':jar')

        then:
        result.wasExecuted("compileRecommendedProductDependencies")
        fileExists("build/libs/${moduleName}.jar")

        def dep = Iterables.getOnlyElement(
                readRecommendedProductDeps(file("build/libs/${moduleName}.jar")).recommendedProductDependencies())
        dep.productGroup == "group"
        dep.productName == "name"
        dep.minimumVersion == "1.0.0"
        dep.maximumVersion == "1.x.x"
    }

    def readRecommendedProductDeps(File jarFile) {
        def zf = new ZipFile(jarFile)
        def resource = zf.getEntry("${RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY}/product-dependencies.json")
        return CompileRecommendedProductDependencies.MAPPER.readValue(zf.getInputStream(resource), RecommendedProductDependencies)
    }

    private static GradleDependencyGenerator generateMavenRepo(String... graph) {
        DependencyGraph dependencyGraph = new DependencyGraph(graph)
        GradleDependencyGenerator generator = new GradleDependencyGenerator(dependencyGraph)
        generator.generateTestMavenRepo()
        return generator
    }
}
