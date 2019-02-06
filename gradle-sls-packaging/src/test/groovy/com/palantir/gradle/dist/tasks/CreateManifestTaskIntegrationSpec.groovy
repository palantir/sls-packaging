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

package com.palantir.gradle.dist.tasks

import com.palantir.gradle.dist.GradleIntegrationSpec
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator

class CreateManifestTaskIntegrationSpec extends GradleIntegrationSpec {

    File mavenRepo

    def setup() {
        generateDependencies()
        buildFile << """
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            import com.palantir.gradle.dist.ProductType

            repositories {
                maven {url "file:///${mavenRepo.getAbsolutePath()}"}
            }

            project.version = '1.0.0'

            task testCreateManifest(type: com.palantir.gradle.dist.tasks.CreateManifestTask) {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = ProductType.SERVICE_V1
                manifestExtensions = [:]
                manifestFile = new File(project.buildDir, "/deployment/manifest.yml")
                productDependenciesConfig = configurations.runtime
            }
        """.stripIndent()
    }

    def 'throws if duplicate dependencies are declared'() {
        setup:
        buildFile << """
            testCreateManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.RawProductDependency("group", "name", "1.0.0", "1.x.x", "1.2.0"), 
                    new com.palantir.gradle.dist.RawProductDependency("group", "name", "1.1.0", "1.x.x", "1.2.0"), 
                ]
            }
        """.stripIndent()

        when:
        def buildResult = runTasksAndFail(':testCreateManifest')

        then:
        buildResult.output.contains('Encountered duplicate declared product')
    }

    def 'throws if declared dependency is also ignored'() {
       setup:
        buildFile << """
            testCreateManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.RawProductDependency("group", "name", "1.0.0", "1.x.x", "1.2.0"), 
                ]
                ignoredProductIds = [
                    new com.palantir.gradle.dist.ProductId("group:name"), 
                ]
            }
        """.stripIndent()

        when:
        def buildResult = runTasksAndFail(':testCreateManifest')

        then:
        buildResult.output.contains('Encountered product dependency declaration that was also ignored')
    }

    def 'Resolve unspecified productDependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }
        """.stripIndent()

        when:
        runTasks(':testCreateManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file("build/deployment/manifest.yml"), Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "group",
                        "product-name"       : "name",
                        "minimum-version"    : "1.0.0",
                        "maximum-version"    : "1.x.x",
                        "recommended-version": "1.2.0"
                ],
                [
                        "product-group"      : "group",
                        "product-name"       : "name2",
                        "minimum-version"    : "2.0.0",
                        "maximum-version"    : "2.x.x",
                        "recommended-version": "2.2.0"
                ]
        ]
    }

    def 'Merges declared productDependencies with discovered dependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }
            
            testCreateManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.RawProductDependency("group", "name", "1.1.0", "1.x.x", "1.2.0"), 
                ]
            }
        """.stripIndent()

        when:
        def result = runTasks(':testCreateManifest')

        then:
        result.output.contains(
                "Encountered a declared product dependency for 'group:name' although there is a discovered dependency")
        def manifest = CreateManifestTask.jsonMapper.readValue(file("build/deployment/manifest.yml"), Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "group",
                        "product-name"       : "name",
                        "minimum-version"    : "1.1.0",
                        "maximum-version"    : "1.x.x",
                        "recommended-version": "1.2.0"
                ],
                [
                        "product-group"      : "group",
                        "product-name"       : "name2",
                        "minimum-version"    : "2.0.0",
                        "maximum-version"    : "2.x.x",
                        "recommended-version": "2.2.0"
                ]
        ]
    }

    def 'Can ignore recommended product dependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }

            tasks.testCreateManifest {
                productDependencies = []
                ignoredProductIds = [
                    new com.palantir.gradle.dist.ProductId("group:name"), 
                    new com.palantir.gradle.dist.ProductId("group:name2")
                ]
            }
        """.stripIndent()

        when:
        runTasks(':testCreateManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file("build/deployment/manifest.yml"), Map)
        manifest.get("extensions").get("product-dependencies").isEmpty()
    }

    def "Merges duplicate discovered dependencies with same version"() {
        setup:
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
                runtime 'd:d:1.0'
            }
        """.stripIndent()

        when:
        runTasks(':testCreateManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(
                file('build/deployment/manifest.yml', projectDir).text, Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "group",
                        "product-name"       : "name2",
                        "minimum-version"    : "2.0.0",
                        "maximum-version"    : "2.x.x",
                        "recommended-version": "2.2.0"
                ]
        ]
    }

    def "Merges duplicate discovered dependencies with different mergeable versions"() {
        setup:
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
                runtime 'e:e:1.0'
            }
        """.stripIndent()

        when:
        runTasks(':testCreateManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file('build/deployment/manifest.yml').text, Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "group",
                        "product-name"       : "name2",
                        "minimum-version"    : "2.1.0",
                        "maximum-version"    : "2.6.x",
                        "recommended-version": "2.2.0"
                ]
        ]
    }

    def "Does not include self dependency"() {
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
            }
            // Configure this service to have the same coordinates as the (sole) dependency coming from b:b:1.0
            tasks.testCreateManifest {
                serviceGroup = "group"
                serviceName = "name2"
            }
        """.stripIndent()

        when:
        runTasks(':testCreateManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file('build/deployment/manifest.yml').text, Map)
        manifest.get("extensions").get("product-dependencies").isEmpty()
    }

    def 'filters out recommended product dependency on self'() {
        setup:
        buildFile << """
        allprojects {
            project.version = '1.0.0-rc1.dirty'
        }
        """
        helper.addSubproject("foo-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'
            
            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'my-service'
                    minimumVersion = rootProject.version
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())
        helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            dependencies {
                compile project(':foo-api')
            }
            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'my-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())

        when:
        runTasks(':foo-server:createManifest', '-i')

        then:
        true
    }

    def generateDependencies() {
        DependencyGraph dependencyGraph = new DependencyGraph(
                "a:a:1.0 -> b:b:1.0|c:c:1.0", "b:b:1.0", "c:c:1.0", "d:d:1.0", "e:e:1.0",
                "pdep:pdep:1.0")
        GradleDependencyGenerator generator = new GradleDependencyGenerator(
                dependencyGraph, new File(projectDir, "build/testrepogen").toString())
        mavenRepo = generator.generateTestMavenRepo()


        // depends on group:name:[1.0.0, 1.x.x]:1.2.0
        Files.copy(
                CreateManifestTaskIntegrationSpec.class.getResourceAsStream("/a-1.0.jar"),
                new File(mavenRepo, "a/a/1.0/a-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        // depends on group:name2:[2.0.0, 2.x.x]:2.2.0
        Files.copy(
                CreateManifestTaskIntegrationSpec.class.getResourceAsStream("/b-1.0.jar"),
                new File(mavenRepo, "b/b/1.0/b-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        // Make d.jar a duplicate of b.jar
        Files.copy(
                CreateManifestTaskIntegrationSpec.class.getResourceAsStream("/b-1.0.jar"),
                new File(mavenRepo, "d/d/1.0/d-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        // e-1.0.jar declares group:name2:[2.1.0, 2.6.x]:2.2.0
        Files.copy(
                CreateManifestTaskIntegrationSpec.class.getResourceAsStream("/b-duplicate-different-versions-1.0.jar"),
                new File(mavenRepo, "e/e/1.0/e-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
    }
}
