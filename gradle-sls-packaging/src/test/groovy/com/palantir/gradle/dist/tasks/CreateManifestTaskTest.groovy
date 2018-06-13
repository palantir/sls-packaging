/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.dist.GradleTestSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files
import java.nio.file.StandardCopyOption

class CreateManifestTaskTest extends GradleTestSpec {

    File mavenRepo

    def 'Fail on missing recommended product dependencies'() {
        setup:
        generateDependencies()
        buildFile << """
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                maven {url "file:///${mavenRepo.getAbsolutePath()}"}
            }

            project.version = '1.0.0'

            dependencies {
                runtime 'a:a:1.0'
            }

            task testCreateManifest(type: com.palantir.gradle.dist.tasks.CreateManifestTask) {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = "service"
                manifestExtensions = [:]
                productDependencies = []
                productDependenciesConfig = configurations.runtime
            }
        """.stripIndent()

        when:
        BuildResult buildResult = run(':testCreateManifest').buildAndFail()

        then:
        buildResult.task(':testCreateManifest').outcome == TaskOutcome.FAILED
        buildResult.output.contains("The following products are recommended as dependencies but do not appear in the " +
                "product dependencies or product dependencies ignored list: [group:name2, group:name]")
    }

    def 'Can ignore recommended product dependencies'() {
        setup:
        generateDependencies()
        buildFile << """
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                maven {url "file:///${mavenRepo.getAbsolutePath()}"}
            }

            project.version = '1.0.0'

            dependencies {
                runtime 'a:a:1.0'
            }

            task testCreateManifest(type: com.palantir.gradle.dist.tasks.CreateManifestTask) {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = "service"
                manifestExtensions = [:]
                productDependencies = []
                productDependenciesConfig = configurations.runtime
                ignoredProductIds = [
                    new com.palantir.gradle.dist.ProductId("group:name"), 
                    new com.palantir.gradle.dist.ProductId("group:name2")
                ]
            }
        """.stripIndent()

        when:
        BuildResult buildResult = run(':testCreateManifest').build()

        then:
        buildResult.task(':testCreateManifest').outcome == TaskOutcome.SUCCESS
    }

    def "Can set product dependencies from recommended product dependencies"() {
        setup:
        generateDependencies()
        buildFile << """
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                maven {url "file:///${mavenRepo.getAbsolutePath()}"}
            }

            project.version = '1.0.0'

            dependencies {
                runtime 'a:a:1.0'
            }

            task testCreateManifest(type: com.palantir.gradle.dist.tasks.CreateManifestTask) {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = "service"
                manifestExtensions = [:]
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name"),
                    new com.palantir.gradle.dist.ProductDependency("group", "name2")
                ]
                productDependenciesConfig = configurations.runtime
            }
        """.stripIndent().replace("{{mavenRepo}}", mavenRepo.getAbsolutePath())

        when:
        runSuccessfully(':testCreateManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(
                file('build/deployment/manifest.yml', projectDir).text, Map)
        manifest.get("extensions").get("product-dependencies").size() == 2
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group": "group",
                        "product-name": "name",
                        "minimum-version": "1.0.0",
                        "maximum-version": "1.x.x",
                        "recommended-version": "1.2.0"
                ],
                [
                        "product-group": "group",
                        "product-name": "name2",
                        "minimum-version": "2.0.0",
                        "maximum-version": "2.x.x",
                        "recommended-version": "2.2.0"
                ]
        ]
    }

    def "Duplicate recommendations with same versions"() {
        setup:
        generateDependencies()
        buildFile << """
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                maven {url "file:///${mavenRepo.getAbsolutePath()}"}
            }

            project.version = '1.0.0'

            dependencies {
                runtime 'b:b:1.0'
                runtime 'd:d:1.0'
            }

            task testCreateManifest(type: com.palantir.gradle.dist.tasks.CreateManifestTask) {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = "service"
                manifestExtensions = [:]
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name2")
                ]
                productDependenciesConfig = configurations.runtime
            }
        """.stripIndent().replace("{{mavenRepo}}", mavenRepo.getAbsolutePath())

        when:
        runSuccessfully(':testCreateManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(
                file('build/deployment/manifest.yml', projectDir).text, Map)
        manifest.get("extensions").get("product-dependencies").size() == 1
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group": "group",
                        "product-name": "name2",
                        "minimum-version": "2.0.0",
                        "maximum-version": "2.x.x",
                        "recommended-version": "2.2.0"
                ]
        ]
    }

    def "Duplicate recommendations with different versions"() {
        setup:
        generateDependencies()
        buildFile << """
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                maven {url "file:///${mavenRepo.getAbsolutePath()}"}
            }

            project.version = '1.0.0'

            dependencies {
                runtime 'b:b:1.0'
                runtime 'e:e:1.0'
            }

            task testCreateManifest(type: com.palantir.gradle.dist.tasks.CreateManifestTask) {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = "service"
                manifestExtensions = [:]
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name2")
                ]
                productDependenciesConfig = configurations.runtime
            }
        """.stripIndent().replace("{{mavenRepo}}", mavenRepo.getAbsolutePath())

        when:
        def result = run(':testCreateManifest').buildAndFail()

        then:
        result.task(':testCreateManifest').outcome == TaskOutcome.FAILED
        result.output.contains(
                "Differing product dependency recommendations found for 'group:name2' in 'e:e:1.0' and 'b:b:1.0'")
    }

    def 'Can create CreateManifestTask when product.version is valid SLS version'() {
        when:
        Project project = ProjectBuilder.builder().build()
        project.version = "1.0.0"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)

        then:
        task.getProjectVersion() == "1.0.0"
    }

    def 'Cannot create CreateManifestTask when product.version is invalid SLS version'() {
        when:
        Project project = ProjectBuilder.builder().build()
        project.version = "1.0.0foo"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)
        task.getProjectVersion() == "1.0.0"

        then:
        IllegalArgumentException exception = thrown()
        exception.message == "Project version must be a valid SLS version: 1.0.0foo"
    }

    def generateDependencies() {
        DependencyGraph dependencyGraph = new DependencyGraph(
                "a:a:1.0 -> b:b:1.0|c:c:1.0", "b:b:1.0", "c:c:1.0", "d:d:1.0", "e:e:1.0")
        GradleDependencyGenerator generator = new GradleDependencyGenerator(dependencyGraph)
        mavenRepo = generator.generateTestMavenRepo()

        Files.copy(
                CreateManifestTaskTest.class.getResourceAsStream("/a-1.0.jar"),
                new File(mavenRepo, "a/a/1.0/a-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        Files.copy(
                CreateManifestTaskTest.class.getResourceAsStream("/b-1.0.jar"),
                new File(mavenRepo, "b/b/1.0/b-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        // Make d.jar a duplicate of b.jar, including the exact same recommendation
        Files.copy(
                CreateManifestTaskTest.class.getResourceAsStream("/b-1.0.jar"),
                new File(mavenRepo, "d/d/1.0/d-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        // Make e.jar a duplicate of b.jar, but with a different recommendation
        Files.copy(
                CreateManifestTaskTest.class.getResourceAsStream("/b-duplicate-different-versions-1.0.jar"),
                new File(mavenRepo, "e/e/1.0/e-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
    }
}
