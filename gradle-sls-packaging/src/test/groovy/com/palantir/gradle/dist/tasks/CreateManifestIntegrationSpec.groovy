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
import com.palantir.gradle.dist.Serializations
import com.palantir.gradle.dist.SlsManifest
import org.gradle.testkit.runner.TaskOutcome

class CreateManifestIntegrationSpec extends AbstractTaskSpec {

    File manifestFile

    def setup() {
        manifestFile = new File(projectDir, 'build/deployment/manifest.yml')
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

        addStandardProductDependency()

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
                compile project(':foo-api')
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

        manifestFile = new File(barDir, 'build/deployment/manifest.yml')

        when:
        def result = runTasks('bar-server:createManifest')

        then:
        result.task(":bar-server:createManifest").outcome == TaskOutcome.SUCCESS
        SlsManifest manifest = readManifest()
        manifest.extensions()["product-dependencies"] == [
                [
                        "product-group"      : "com.palantir.group",
                        "product-name"       : "foo-service",
                        "minimum-version"    : "0.0.0",
                        "recommended-version": "1.0.1",
                        "maximum-version"    : "1.x.x",
                        "optional"           : false
                ]
        ]

        file('bar-server/product-dependencies.lock').text.contains 'com.palantir.group:foo-service ($projectVersion, 1.x.x)'
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
                ":foo-server:resolveProductDependencies",
                ":foo-server:createManifest")
    }

    def "check depends on createManifest"() {
        when:
        def result = runTasks(':check')

        then:
        result.task(":createManifest") != null
    }

    private SlsManifest readManifest() {
        assert manifestFile.exists()
        return Serializations.readSlsManifest(manifestFile)
    }
}
