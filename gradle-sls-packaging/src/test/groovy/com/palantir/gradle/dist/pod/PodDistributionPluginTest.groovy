package com.palantir.gradle.dist.pod

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.palantir.gradle.dist.GradleTestSpec
import org.gradle.testkit.runner.BuildResult

class PodDistributionPluginTest extends GradleTestSpec {

    def 'manifest file contains expected fields'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                serviceName 'pod-name'
            }
        '''.stripIndent()

        when:
        runSuccessfully(':configTar', ':untar')

        then:
        String manifest = file('dist/pod-name-0.0.1/deployment/manifest.yml', projectDir).text
        manifest.contains('"manifest-version": "1.0"')
        manifest.contains('"product-group": "service-group"')
        manifest.contains('"product-name": "pod-name"')
        manifest.contains('"product-version": "0.0.1"')
        manifest.contains('"product-type": "pod.v1"')
    }

    def 'pod file contains expected fields'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                serviceName 'pod-name'
                
                service "bar-service", {
                  productGroup = "com.palantir.foo"
                  productName = "bar"
                  productVersion = "1.0.0"
                  volumeMap = ["bar-volume": "random-volume"]
                }
                service "baz-service", {
                  productGroup = "com.palantir.foo"
                  productName = "baz"
                  productVersion = "1.0.0"
                  volumeMap = ["baz-volume": "random-volume"]
                }
                
                volume "random-volume", {
                  desiredSize = "10 GB"
                }
            }
        '''.stripIndent()

        when:
        runSuccessfully(':configTar', ':untar')

        then:
        String pod = file('dist/pod-name-0.0.1/deployment/pod.yml', projectDir).text

        // verify pod YAML file contents
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        def podYaml = mapper.readTree(pod)

        podYaml.has("services")
        podYaml.get("services").has("bar-service")
        podYaml.get("services").has("baz-service")
        podYaml.get("services").get("bar-service").get("product-name").asText().contains("bar")
        podYaml.get("services").get("bar-service").get("product-group").asText().contains("com.palantir.foo")
        podYaml.has("volumes")
        podYaml.get("volumes").get("random-volume").get("desired-size").asText().contains("10 GB")
    }

    def 'pod file creation fails with bad service names'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                serviceName 'pod-name'
                
                service "barService", {
                  productGroup = "com.palantir.foo"
                  productName = "bar"
                  productVersion = "1.0.0"
                  volumeMap = ["bar-volume": "random-volume"]
                }
                service "bazService", {
                  productGroup = "com.palantir.foo"
                  productName = "baz"
                  productVersion = "1.0.0"
                  volumeMap = ["baz-volume": "random-volume"]
                }
                
                volume "random-volume", {
                  desiredSize = "10 GB"
                }
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = run(':configTar').buildAndFail()

        then:
        buildResult.getOutput().contains("Pod validation failed for service barService: service names must be kebab case")
    }

    def 'pod file creation fails with no product group'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                serviceName 'pod-name'
                service "bar-service", {
                  productName = "bar"
                  productVersion = "1.0.0"
                  volumeMap = ["bar-volume": "random-volume"]
                }                
                volume "random-volume", {
                  desiredSize = "10 GB"
                }
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = run(':configTar').buildAndFail()

        then:
        buildResult.getOutput().contains("Pod validation failed for service bar-service: product group must be specified for pod service")
    }

    def 'pod file creation fails with no product name'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                serviceName 'pod-name'
                service "bar-service", {
                  productGroup = "com.palantir.foo"
                  productVersion = "1.0.0"
                  volumeMap = ["bar-volume": "random-volume"]
                }                
                volume "random-volume", {
                  desiredSize = "10 GB"
                }
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = run(':configTar').buildAndFail()

        then:
        buildResult.getOutput().contains("Pod validation failed for service bar-service: product name must be specified for pod service")
    }

    def 'pod file creation fails with no product version '() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                serviceName 'pod-name'
                service "bar-service", {
                  productGroup = "com.palantir.foo"
                  productName = "bar"
                  volumeMap = ["bar-volume": "random-volume"]
                }                
                volume "random-volume", {
                  desiredSize = "10 GB"
                }
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = run(':configTar').buildAndFail()

        then:
        buildResult.getOutput().contains("Pod validation failed for service bar-service: product version must be specified for pod service")
    }

    def 'pod file creation fails with bad volume mappings'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                serviceName 'pod-name'
                
                service "bar-service", {
                  productGroup = "com.palantir.foo"
                  productName = "bar"
                  productVersion = "1.0.0"
                  volumeMap = ["bar-volume": "random-volume"]
                }
                service "baz-service", {
                  productGroup = "com.palantir.foo"
                  productName = "baz"
                  productVersion = "1.0.0"
                  volumeMap = ["baz-volume": "not-a-defined-volume"]
                }
                
                volume "random-volume", {
                  desiredSize = "10 GB"
                }
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = run(':configTar').buildAndFail()

        then:
        buildResult.getOutput().contains("Pod validation failed for service baz-service: service volume mapping cannot contain undeclared volumes")
    }
    private static createUntarBuildFile(buildFile) {
        buildFile << '''
            plugins {
                id 'com.palantir.sls-pod-distribution'
            }
            
            distribution {
                serviceName 'pod-name'
            }

            version "0.0.1"
            project.group = 'service-group'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/pod-name-0.0.1.pod.config.tgz"))
                into "${projectDir}/dist"
                dependsOn configTar
            }
        '''.stripIndent()
    }
}

