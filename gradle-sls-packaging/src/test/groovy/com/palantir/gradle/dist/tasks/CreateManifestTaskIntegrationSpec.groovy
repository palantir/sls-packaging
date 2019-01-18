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

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator

class CreateManifestTaskIntegrationSpec extends IntegrationSpec {

    File mavenRepo

    def setup() {
        generateDependencies()
        buildFile << """
            apply plugin: 'com.palantir.sls-java-service-distribution'

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

    def 'Fail on missing recommended product dependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }

            tasks.testCreateManifest {
                productDependencies = []
            }
        """.stripIndent()

        when:
        def buildResult = runTasksWithFailure(':testCreateManifest')

        then:
        buildResult.getStandardError().contains("The following products are recommended as dependencies but do not appear in the " +
                "product dependencies or product dependencies ignored list: [group:name2, group:name]")
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
        def buildResult = runTasksSuccessfully(':testCreateManifest')

        then:
        buildResult.wasExecuted(':testCreateManifest')
    }

    def "Can set product dependencies from recommended product dependencies"() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }

            tasks.testCreateManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name"),
                    new com.palantir.gradle.dist.ProductDependency("group", "name2")
                ]
            }
        """.stripIndent()

        when:
        runTasksSuccessfully(':testCreateManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(
                file('build/deployment/manifest.yml', projectDir).text, Map)
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

    def "Duplicate recommendations with same versions"() {
        setup:
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
                runtime 'd:d:1.0'
            }

            tasks.testCreateManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name2")
                ]
            }
        """.stripIndent()

        when:
        runTasksSuccessfully(':testCreateManifest')

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

    def "Duplicate recommendations with different mergeable versions"() {
        setup:
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
                runtime 'e:e:1.0'
            }
            tasks.testCreateManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name2")
                ]
            }
        """.stripIndent()

        when:
        runTasksSuccessfully(':testCreateManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(
                file('build/deployment/manifest.yml', projectDir).text, Map)
        manifest.get("extensions").get("product-dependencies").size() == 1
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

    def generateDependencies() {
        DependencyGraph dependencyGraph = new DependencyGraph(
                "a:a:1.0 -> b:b:1.0|c:c:1.0", "b:b:1.0", "c:c:1.0", "d:d:1.0", "e:e:1.0",
                "pdep:pdep:1.0")
        GradleDependencyGenerator generator = new GradleDependencyGenerator(dependencyGraph)
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
