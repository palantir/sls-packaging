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

import com.fasterxml.jackson.core.type.TypeReference
import com.palantir.gradle.dist.GradleTestVersions
import com.palantir.gradle.dist.ObjectMappers
import com.palantir.gradle.dist.SlsManifest
import com.palantir.gradle.dist.artifacts.JsonArtifactLocator
import com.palantir.gradle.dist.pdeps.ResolveProductDependenciesIntegrationSpec
import nebula.test.IntegrationSpec
import spock.lang.Unroll

class CreateManifestTaskIntegrationSpec extends IntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: 'com.palantir.sls-java-service-distribution'

            import com.palantir.gradle.dist.ProductType

            project.version = '1.0.0'
            
            distribution {
                serviceName "serviceName"
                serviceGroup "serviceGroup"
            }
        """.stripIndent(true)
    }

    def '#gradleVersionNumber: fails if lockfile is not up to date'() {
        setup:
        gradleVersion = gradleVersionNumber
        buildFile << """
        distribution {
            ${ResolveProductDependenciesIntegrationSpec.PDEP}
        }
        """.stripIndent(true)

        file('product-dependencies.lock').text = """\
        # Run ./gradlew writeProductDependenciesLocks to regenerate this file
        product-id: serviceGroup:serviceName
        group:name2 (2.0.0, 2.x.x)
        """.stripIndent(true)

        when:
        def buildResult = runTasksWithFailure(':createManifest')

        then:
        buildResult.getStandardError().contains(
                "product-dependencies.lock is out of date, please run `./gradlew writeProductDependenciesLocks` to update it")

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: fails if unexpected lockfile exists'() {
        setup:
        gradleVersion = gradleVersionNumber

        runTasksSuccessfully('createManifest') // ensure task is run once
        def result = runTasksSuccessfully('createManifest')
        result.wasUpToDate(':createManifest')

        when:
        file('product-dependencies.lock') << '\nthis should not be here'

        then:
        runTasksWithFailure('createManifest')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: fails if lock file disappears'() {
        setup:
        gradleVersion = gradleVersionNumber

        buildFile << """
        distribution {
            ${ResolveProductDependenciesIntegrationSpec.PDEP}
        }
        """.stripIndent(true)

        file('product-dependencies.lock').text = """\
        # Run ./gradlew writeProductDependenciesLocks to regenerate this file
        product-id: serviceGroup:serviceName
        group1:name1 (1.0.0, 1.3.x)
        """.stripIndent(true)

        runTasksSuccessfully('createManifest') // ensure task is run once
        runTasksSuccessfully('createManifest')

        when:
        file('product-dependencies.lock').delete()

        then:
        runTasksWithFailure('createManifest')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: fails if lockfile has changed contents'() {
        setup:
        gradleVersion = gradleVersionNumber

        buildFile << """
        distribution {
            ${ResolveProductDependenciesIntegrationSpec.PDEP}
        }
        """.stripIndent(true)

        file('product-dependencies.lock').text = """\
        # Run ./gradlew writeProductDependenciesLocks to regenerate this file
        product-id: serviceGroup:serviceName
        group1:name1 (1.0.0, 1.3.x)
        """.stripIndent(true)

        runTasksSuccessfully('createManifest') // ensure task is run once
        runTasksSuccessfully('createManifest')

        when:
        file('product-dependencies.lock') << '\nthis should not be here'

        then:
        runTasksWithFailure('createManifest')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: always write projectVersion as minimum version in product dependency that is published by this repo'() {
        setup:
        gradleVersion = gradleVersionNumber

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
        """.stripIndent(true))
        helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'foo-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent(true))
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
        """.stripIndent(true))

        file('product-dependencies.lock', barDir).text = """\
        # Run ./gradlew writeProductDependenciesLocks to regenerate this file
        product-id: com.palantir.group:bar-service
        com.palantir.group:foo-service (\$projectVersion, 1.x.x)
        """.stripIndent(true)

        when:
        def result = runTasksSuccessfully('bar-server:createManifest')

        then:
        def manifest = ObjectMappers.jsonMapper.readValue(file('build/deployment/manifest.yml', barDir).text, Map)
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

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    @Unroll
    def '#gradleVersionNumber: writes locks when #writeLocksTask is on the command line'() {
        setup:
        gradleVersion = gradleVersionNumber

        buildFile << """
        distribution {
            ${ResolveProductDependenciesIntegrationSpec.PDEP}
        }
        """.stripIndent(true)

        when:
        def buildResult = runTasksSuccessfully(writeLocksTask)

        then:
        buildResult.wasExecuted(':createManifest')
        file('product-dependencies.lock').text == """\
        # Run ./gradlew writeProductDependenciesLocks to regenerate this file
        product-id: serviceGroup:serviceName
        group1:name1 (1.0.0, 1.3.x)
        """.stripIndent(true)

        where:
        [gradleVersionNumber, writeLocksTask] << [
                GradleTestVersions.GRADLE_VERSIONS,
                ['--write-locks', 'writeProductDependenciesLocks', 'wPDL']
        ].combinations()
    }

    def '#gradleVersionNumber: write artifacts to manifest'() {
        setup:
        gradleVersion = gradleVersionNumber

        buildFile << """
        distribution {
            artifact {
                type = "oci"
                uri = "registry.example.io/foo/bar:v1.3.0"
            }
        }
        """.stripIndent(true)

        when:
        def buildResult = runTasksSuccessfully('createManifest')

        then:
        buildResult.wasExecuted('createManifest')
        readArtifactsExtension() == [JsonArtifactLocator.from("oci", "registry.example.io/foo/bar:v1.3.0")]

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: fails if invalid'() {
        setup:
        gradleVersion = gradleVersionNumber

        buildFile << """
        distribution {
            artifact {
                type = "oci"
                uri = "registry.example[.io/foo/bar:v1.3.0"
            }
        }
        """.stripIndent(true)

        when:
        def buildResult = runTasksWithFailure('createManifest')

        then:
        buildResult.failure.cause.cause.message.contains("uri is not valid")

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def "#gradleVersionNumber: write artifacts to manifest from task output"() {
        given:
        gradleVersion = gradleVersionNumber
        buildFile << """
            
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.provider.Provider
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import java.nio.file.Files
            
            abstract class SomeTask extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutput();
                
                final Provider<String> artifactUri() {
                  return getOutput().getAsFile().map { 
                    return Files.readString(it.toPath())
                  }
                }
                
                @TaskAction
                final void action() throws Exception {
                    Files.writeString(getOutput().getAsFile().get().toPath(), "registry.example.io/foo/bar:v1.3.0")
                }
            }
            
            def artifactOutput = tasks.register('produceArtifactUrl', SomeTask.class) {
                output.fileValue(file("build/artifact-url"))
            }
            
            distribution {
                artifact {
                    type = "oci"
                    uri = artifactOutput.flatMap { it.output }.map { Files.readString(it.getAsFile().toPath())}
                }
            }
        """.stripIndent(true)

        when:
        def buildResult = runTasksSuccessfully('createManifest')
        println buildResult.standardOutput

        then:
        readArtifactsExtension() == [JsonArtifactLocator.from("oci", "registry.example.io/foo/bar:v1.3.0")]

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def "#gradleVersionNumber: createManifest depends on task whose artifacts are added to the extension via addAllLater"() {
        given:
        gradleVersion = gradleVersionNumber
        buildFile << """
            import com.palantir.gradle.dist.artifacts.ArtifactLocator
            import org.gradle.api.file.RegularFileProperty
            import org.gradle.api.tasks.OutputFile
            import org.gradle.api.tasks.TaskAction
            import java.nio.file.Files

            abstract class ProduceArtifactsTask extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutput();

                @TaskAction
                final void action() throws Exception {
                    Files.writeString(getOutput().getAsFile().get().toPath(), "registry.example.io/foo/bar:v1.3.0")
                }
            }

            def produceArtifacts = tasks.register('produceArtifacts', ProduceArtifactsTask) {
                output.fileValue(file("build/artifact-url"))
            }

            distribution.artifacts.addAllLater(produceArtifacts.map { producer ->
                def locator = project.objects.newInstance(ArtifactLocator)
                locator.type.set("oci")
                locator.uri.set(Files.readString(producer.output.getAsFile().get().toPath()))
                return [locator]
            })
        """.stripIndent(true)

        when:
        def buildResult = runTasksSuccessfully('createManifest')

        then:
        buildResult.wasExecuted(':produceArtifacts')
        readArtifactsExtension() == [JsonArtifactLocator.from("oci", "registry.example.io/foo/bar:v1.3.0")]

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    private List<JsonArtifactLocator> readArtifactsExtension() {
        def manifest = ObjectMappers.jsonMapper.readValue(file('build/deployment/manifest.yml').text, SlsManifest)
        ObjectMappers.jsonMapper.convertValue(manifest.extensions().get("artifacts"), new TypeReference<List<JsonArtifactLocator>>() {})
    }

    def "#gradleVersionNumber: check depends on createManifest"() {
        setup:
        gradleVersion = gradleVersionNumber

        when:
        def result = runTasks(':check')

        then:
        result.wasExecuted(":createManifest")

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }
}
