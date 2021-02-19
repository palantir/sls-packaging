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

import com.google.common.collect.ImmutableSet
import com.palantir.gradle.dist.GradleIntegrationSpec
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

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

            // If we create a custom task and then do --write-locks, the original task will be invoked anyway
            // So, let's just configure the original task, yea?
            tasks.createManifest {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = ProductType.SERVICE_V1
                manifestExtensions = [:]
                manifestFile = new File(project.buildDir, "/deployment/manifest.yml")
            }
        """.stripIndent()
    }

    def 'fails if lockfile is not up to date'() {
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
            }
        """.stripIndent()

        file('product-dependencies.lock').text = """\
            # Run ./gradlew --write-locks to regenerate this file
            group:name2 (2.0.0, 2.x.x)
        """.stripIndent()

        // run it first to ensure cache is warmed up
        runTasks(':createManifest')

        buildFile << """
            createManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name", "1.0.0", "1.x.x", "1.2.0"),
                ]
            }
        """.stripIndent()

        when:
        def buildResult = runTasksAndFail(':createManifest')

        then:
        buildResult.output.contains(
                "product-dependencies.lock is out of date, please run `./gradlew createManifest --write-locks` to update it")
    }

    def 'fails if unexpected lockfile exists'() {
        runTasks('createManifest') // ensure task is run once
        runTasks('createManifest').task(':createManifest').outcome == TaskOutcome.UP_TO_DATE

        when:
        file('product-dependencies.lock') << '\nthis should not be here'

        then:
        runTasksAndFail('createManifest').task(':createManifest').outcome == TaskOutcome.FAILED
    }

    def 'fails if lock file disappears'() {
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
            }
        """.stripIndent()

        file('product-dependencies.lock').text = """\
            # Run ./gradlew --write-locks to regenerate this file
            group:name2 (2.0.0, 2.x.x)
        """.stripIndent()

        runTasks('createManifest') // ensure task is run once
        runTasks('createManifest').task(':createManifest').outcome == TaskOutcome.UP_TO_DATE

        when:
        file('product-dependencies.lock').delete()

        then:
        runTasksAndFail('createManifest').task(':createManifest').outcome == TaskOutcome.FAILED
    }

    def 'fails if lockfile has changed contents'() {
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
            }
        """.stripIndent()

        file('product-dependencies.lock').text = """\
            # Run ./gradlew --write-locks to regenerate this file
            group:name2 (2.0.0, 2.x.x)
        """.stripIndent()

        runTasks('createManifest') // ensure task is run once
        runTasks('createManifest').task(':createManifest').outcome == TaskOutcome.UP_TO_DATE

        when:
        file('product-dependencies.lock') << '\nthis should not be here'

        then:
        runTasksAndFail('createManifest').task(':createManifest').outcome == TaskOutcome.FAILED
    }

    def 'merges declared product dependencies'() {
        setup:
        buildFile << """
            createManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name", "1.0.0", "1.x.x", "1.2.0"), 
                    new com.palantir.gradle.dist.ProductDependency("group", "name", "1.1.0", "1.x.x", "1.2.0"), 
                ]
            }
        """.stripIndent()

        when:
        def buildResult = runTasks(':createManifest', '--write-locks')

        then:
        buildResult.task(':createManifest').outcome == TaskOutcome.SUCCESS
    }

    def 'throws if declared dependency is also ignored'() {
        setup:
        buildFile << """
            createManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name", "1.0.0", "1.x.x", "1.2.0"), 
                ]
                ignoredProductIds = [
                    new com.palantir.gradle.dist.ProductId("group:name"), 
                ]
            }
        """.stripIndent()

        when:
        def buildResult = runTasksAndFail(':createManifest')

        then:
        buildResult.output.contains('Encountered product dependency declaration that was also ignored')
    }

    def 'throws if declared dependency is also optional'() {
        setup:
        buildFile << """
            createManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name", "1.0.0", "1.x.x", "1.2.0"), 
                ]
                optionalProductIds = [
                    new com.palantir.gradle.dist.ProductId("group:name"), 
                ]
            }
        """.stripIndent()

        when:
        def buildResult = runTasksAndFail(':createManifest')

        then:
        buildResult.output.contains('Encountered product dependency declaration that was also declared as optional')
    }

    def 'Resolve unspecified productDependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }
        """.stripIndent()
        file('product-dependencies.lock').text = """\
        # Run ./gradlew --write-locks to regenerate this file
        group:name (1.0.0, 1.x.x)
        group:name2 (2.0.0, 2.x.x)
        """.stripIndent()

        when:
        runTasks(':createManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file("build/deployment/manifest.yml"), Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "group",
                        "product-name"       : "name",
                        "minimum-version"    : "1.0.0",
                        "recommended-version": "1.2.0",
                        "maximum-version"    : "1.x.x",
                        "optional"           : false
                ],
                [
                        "product-group"      : "group",
                        "product-name"       : "name2",
                        "minimum-version"    : "2.0.0",
                        "recommended-version": "2.2.0",
                        "maximum-version"    : "2.x.x",
                        "optional"           : false
                ]
        ]
    }

    def 'Merges declared productDependencies with discovered dependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }
            
            createManifest {
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name", "1.1.0", "1.x.x", "1.2.0", true), 
                ]
            }
        """.stripIndent()
        file('product-dependencies.lock').text = """\
        # Run ./gradlew --write-locks to regenerate this file
        group:name (1.1.0, 1.x.x) optional
        group:name2 (2.0.0, 2.x.x)
        """.stripIndent()

        when:
        def result = runTasks(':createManifest')

        then:
        !result.output.contains(
                "Please remove your declared product dependency on 'group:name' because it is already provided by a jar dependency")
        def manifest = CreateManifestTask.jsonMapper.readValue(file("build/deployment/manifest.yml"), Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "group",
                        "product-name"       : "name",
                        "minimum-version"    : "1.1.0",
                        "recommended-version": "1.2.0",
                        "maximum-version"    : "1.x.x",
                        "optional"           : true

                ],
                [
                        "product-group"      : "group",
                        "product-name"       : "name2",
                        "minimum-version"    : "2.0.0",
                        "recommended-version": "2.2.0",
                        "maximum-version"    : "2.x.x",
                        "optional"           : false
                ]
        ]
    }

    def 'Can ignore recommended product dependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }

            tasks.createManifest {
                productDependencies = []
                ignoredProductIds = [
                    new com.palantir.gradle.dist.ProductId("group:name"), 
                    new com.palantir.gradle.dist.ProductId("group:name2")
                ]
            }
        """.stripIndent()
        file('product-dependencies.lock').delete()

        when:
        runTasks(':createManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file("build/deployment/manifest.yml"), Map)
        manifest.get("extensions").get("product-dependencies").isEmpty()
    }

    def 'Mark as optional product dependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }

            tasks.createManifest {
                optionalProductIds = [
                    new com.palantir.gradle.dist.ProductId("group:name"), 
                    new com.palantir.gradle.dist.ProductId("group:name2")
                ]
            }
        """.stripIndent()
        file('product-dependencies.lock').text = """\
        # Run ./gradlew --write-locks to regenerate this file
        group:name (1.0.0, 1.x.x) optional
        group:name2 (2.0.0, 2.x.x) optional
        """.stripIndent()

        when:
        runTasks(':createManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file("build/deployment/manifest.yml"), Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "group",
                        "product-name"       : "name",
                        "minimum-version"    : "1.0.0",
                        "recommended-version": "1.2.0",
                        "maximum-version"    : "1.x.x",
                        "optional"           : true

                ],
                [
                        "product-group"      : "group",
                        "product-name"       : "name2",
                        "minimum-version"    : "2.0.0",
                        "recommended-version": "2.2.0",
                        "maximum-version"    : "2.x.x",
                        "optional"           : true
                ]
        ]
    }

    def "Merges duplicate discovered dependencies with same version"() {
        setup:
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
                runtime 'd:d:1.0'
            }
        """.stripIndent()
        file('product-dependencies.lock').text = """\
        # Run ./gradlew --write-locks to regenerate this file
        group:name2 (2.0.0, 2.x.x)
        """.stripIndent()

        when:
        runTasks(':createManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(
                file('build/deployment/manifest.yml', projectDir).text, Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "group",
                        "product-name"       : "name2",
                        "minimum-version"    : "2.0.0",
                        "recommended-version": "2.2.0",
                        "maximum-version"    : "2.x.x",
                        "optional"           : false
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
        file('product-dependencies.lock').text = """\
        # Run ./gradlew --write-locks to regenerate this file
        group:name2 (2.1.0, 2.6.x)
        """.stripIndent()

        when:
        runTasks(':createManifest')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file('build/deployment/manifest.yml').text, Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "group",
                        "product-name"       : "name2",
                        "minimum-version"    : "2.1.0",
                        "recommended-version": "2.2.0",
                        "maximum-version"    : "2.6.x",
                        "optional"           : false
                ]
        ]
    }

    def "Does not include self dependency"() {
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
            }
            // Configure this service to have the same coordinates as the (sole) dependency coming from b:b:1.0
            tasks.createManifest {
                serviceGroup = "group"
                serviceName = "name2"
            }
        """.stripIndent()
        file('product-dependencies.lock').delete()

        when:
        runTasks(':createManifest', '--write-locks')

        then:
        def manifest = CreateManifestTask.jsonMapper.readValue(file('build/deployment/manifest.yml').text, Map)
        manifest.get("extensions").get("product-dependencies").isEmpty()

        !fileExists('product-dependencies.lock')
    }

    @Unroll
    def 'filters out recommended product dependency on self (version: #projectVersion)'() {
        setup:
        buildFile << """
        allprojects {
            project.version = '$projectVersion'
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
        runTasks(':foo-server:createManifest', '-i', '--write-locks')

        then: 'foo-server does not include transitively discovered self dependency'
        !fileExists('foo-server/product-dependencies.lock')

        where:
        projectVersion << ['1.0.0-rc1.dirty', '1.0.0']
    }

    @Unroll
    def 'masks minimum version in product dependency that is published by this repo if same as project version (#projectVersion)'() {
        setup:
        buildFile << """
        allprojects {
            project.version = '$projectVersion'
        }
        """
        helper.addSubproject("foo-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'
            
            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'foo-service'
                    minimumVersion = rootProject.version
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
        helper.addSubproject("bar-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            dependencies {
                compile project(':foo-api')
            }
            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'bar-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())

        when:
        runTasks('--write-locks')

        then:
        file('bar-server/product-dependencies.lock').readLines().contains 'com.palantir.group:foo-service ($projectVersion, 1.x.x)'

        where:
        projectVersion << ['1.0.0-rc1.dirty', '1.0.0']
    }

    def 'does not mask minimum version in product dependency that is published by this repo if different from project version'() {
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
        helper.addSubproject("bar-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            dependencies {
                compile project(':foo-api')
            }
            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'bar-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())

        when:
        runTasks('--write-locks')

        then:
        file('bar-server/product-dependencies.lock').readLines().contains 'com.palantir.group:foo-service (0.0.0, 1.x.x)'
    }

    def 'merging two dirty product dependencies is not acceptable'() {
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
                    productName = 'foo-service'
                    minimumVersion = '0.0.0.dirty'
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())

        helper.addSubproject("other-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            dependencies {
                compile project(':foo-api')
            }
            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'other-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
                
                // Incompatible with the 0.0.0.dirty from the classpath
                productDependency('com.palantir.group', 'foo-service', '1.0.0.dirty', '1.x.x')
            }
        """.stripIndent())

        when:
        def result = runTasksAndFail('--write-locks')

        then:
        result.output.contains('Could not determine minimum version among two non-orderable minimum versions')
    }

    def "createManifest does not force compilation of sibling projects"() {
        setup:
        buildFile << """
        allprojects {
            project.version = '1.0.0'
            group "com.palantir.group"
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
            
            dependencies {
                compile project(':foo-api')
            }

            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'foo-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())

        when:
        def result = runTasks('foo-server:createManifest')

        then:
        result.task(":foo-server:createManifest").outcome == TaskOutcome.SUCCESS
        result.task(":foo-api:configureProductDependencies").outcome == TaskOutcome.SUCCESS
        result.task(':foo-api:jar') == null
        result.tasks.collect({ it.path }).toSet() == ImmutableSet.of(
                ":foo-api:configureProductDependencies",
                ":foo-api:processResources",
                ":foo-server:mergeDiagnosticsJson",
                ":foo-server:createManifest")
    }

    def "createManifest discovers in repo product dependencies"() {
        setup:
        buildFile << """
        allprojects {
            project.version = '1.0.0'
            group "com.palantir.group"
        }
        """
        helper.addSubproject("bar-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'bar-service'
                    minimumVersion = '0.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())
        helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            
            dependencies {
                compile project(':bar-api')
            }

            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'foo-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())

        when:
        def result = runTasks('foo-server:createManifest', '--write-locks')

        then:
        result.task(":foo-server:createManifest").outcome == TaskOutcome.SUCCESS
        result.task(':bar-api:jar') == null

        def manifest = CreateManifestTask.jsonMapper.readValue(file("foo-server/build/deployment/manifest.yml"), Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "com.palantir.group",
                        "product-name"       : "bar-service",
                        "minimum-version"    : "0.0.0",
                        "recommended-version": "1.0.0",
                        "maximum-version"    : "1.x.x",
                        "optional"           :  false
                ],
        ]
    }

    def "createManifest discovers transitive in repo dependencies"() {
        setup:
        buildFile << """
        allprojects {
            project.version = '1.0.0'
            group "com.palantir.group"
        }
        """
        helper.addSubproject("bar-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'bar-service'
                    minimumVersion = '0.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())
        helper.addSubproject("bar-lib", """
            apply plugin: 'java'
            dependencies {
                compile project(':bar-api')
            }
        """.stripIndent())
        helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            
            dependencies {
                compile project(':bar-lib')
            }

            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'foo-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())

        when:
        def result = runTasks('foo-server:createManifest', '--write-locks')

        then:
        result.task(":foo-server:createManifest").outcome == TaskOutcome.SUCCESS
        result.task(':bar-api:jar') == null

        def manifest = CreateManifestTask.jsonMapper.readValue(file("foo-server/build/deployment/manifest.yml"), Map)
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group"      : "com.palantir.group",
                        "product-name"       : "bar-service",
                        "minimum-version"    : "0.0.0",
                        "recommended-version": "1.0.0",
                        "maximum-version"    : "1.x.x",
                        "optional"           : false
                ],
        ]
    }

    def "check depends on createManifest"() {
        when:
        def result = runTasks(':check')

        then:
        result.task(":createManifest") != null
    }

    def 'handles multiple product dependencies when project version is dirty'() {
        setup:
        // Set project version to be non orderable sls version
        buildFile << """
        allprojects {
            project.version = '1.0.0.dirty'
            group "com.palantir.group"
        }
        """
        helper.addSubproject('bar-service', '''
            apply plugin: 'com.palantir.sls-java-service-distribution'
            
            dependencies {
                compile project(':bar-api')
            }

            distribution {
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
            }
        '''.stripIndent())
        helper.addSubproject('bar-lib', '''
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'bar-service'
                    minimumVersion = project.version
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        '''.stripIndent())
        helper.addSubproject("bar-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'bar-service'
                    minimumVersion = '0.5.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())
        helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            
            dependencies {
                compile project(':bar-api')
                compile project(':bar-lib')
            }

            distribution {
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
            }
        """.stripIndent())

        when:
        def result = runTasks('foo-server:createManifest', '--write-locks')

        then:
        result.task(":foo-server:createManifest").outcome == TaskOutcome.SUCCESS

        file('foo-server/product-dependencies.lock').text.contains 'com.palantir.group:bar-service ($projectVersion, 1.x.x)'
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
