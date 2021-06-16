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
            
            distribution {
                serviceName "serviceName"
                serviceGroup "serviceGroup"
            }
        """.stripIndent()
    }

    def 'fails if lockfile is not up to date'() {
        buildFile << """
            dependencies {
                implementation 'b:b:1.0'
            }
        """.stripIndent()

        file('product-dependencies.lock').text = """\
            # Run ./gradlew --write-locks to regenerate this file
            group:name2 (2.0.0, 2.x.x)
        """.stripIndent()

        when:
        def buildResult = runTasksWithFailure(':createManifest')

        then:
        buildResult.getStandardError().contains(
                "product-dependencies.lock is out of date, please run `./gradlew createManifest --write-locks` to update it")
    }

    def 'fails if unexpected lockfile exists'() {
        runTasksSuccessfully('createManifest') // ensure task is run once
        def result = runTasksSuccessfully('createManifest')
        result.wasUpToDate(':createManifest')

        when:
        file('product-dependencies.lock') << '\nthis should not be here'

        then:
        runTasksWithFailure('createManifest')
    }

    def 'fails if lock file disappears'() {
        buildFile << """
            dependencies {
                implementation 'b:b:1.0'
            }
        """.stripIndent()

        file('product-dependencies.lock').text = """\
        # Run ./gradlew --write-locks to regenerate this file
        group:name2 (2.0.0, 2.x.x)
        """.stripIndent()

        runTasksSuccessfully('createManifest') // ensure task is run once
        runTasksSuccessfully('createManifest')

        when:
        file('product-dependencies.lock').delete()

        then:
        runTasksWithFailure('createManifest')
    }

    def 'fails if lockfile has changed contents'() {
        buildFile << """
            dependencies {
                implementation 'b:b:1.0'
            }
        """.stripIndent()

        file('product-dependencies.lock').text = """\
        # Run ./gradlew --write-locks to regenerate this file
        group:name2 (2.0.0, 2.x.x)
        """.stripIndent()

        runTasksSuccessfully('createManifest') // ensure task is run once
        runTasksSuccessfully('createManifest')

        when:
        file('product-dependencies.lock') << '\nthis should not be here'

        then:
        runTasksWithFailure('createManifest')
    }

    def 'always write projectVersion as minimum version in product dependency that is published by this repo'() {
        setup:
        buildFile << """
        allprojects {
            project.version = '1.0.1'
        }
        """

        helper.addSubproject("foo-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'
            
            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'foo-service'
                    minimumVersion = '0.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())
        helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'foo-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())
        def barDir = helper.addSubproject("bar-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            dependencies {
                implementation project(':foo-api')
            }
            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'bar-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())

        file('product-dependencies.lock', barDir).text = """\
        # Run ./gradlew --write-locks to regenerate this file
        com.palantir.group:foo-service (\$projectVersion, 1.x.x)
        """.stripIndent()

        when:
        def result = runTasksSuccessfully('bar-server:createManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file('build/deployment/manifest.yml', barDir).text, Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "com.palantir.group",
                        "product-name"       : "foo-service",
                        "minimum-version"    : "0.0.0",
                        "recommended-version": "1.0.1",
                        "maximum-version"    : "1.x.x",
                        "optional"           : false
                ]
        ]
    }

    def "check depends on createManifest"() {
        when:
        def result = runTasks(':check')

        then:
        result.wasExecuted(":createManifest")
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
