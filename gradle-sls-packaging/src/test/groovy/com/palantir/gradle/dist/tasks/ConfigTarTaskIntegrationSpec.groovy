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

import com.google.common.base.Throwables
import nebula.test.IntegrationSpec

class ConfigTarTaskIntegrationSpec extends IntegrationSpec {

    def 'configTar task exists for services'() {
        setup:
        createUntarBuildFile(buildFile, "java-service", "service", "foo-service")

        when:
        runTasksSuccessfully(':configTar')

        then:
        fileExists('build/distributions/foo-service-0.0.1.service.config.tgz')
    }

    def 'configTar task exists for assets'() {
        setup:
        createUntarBuildFile(buildFile, "asset", "asset", "foo-asset")

        when:
        runTasksSuccessfully(':configTar')

        then:
        fileExists('build/distributions/foo-asset-0.0.1.asset.config.tgz')
    }

    def 'configTar task contains the necessary deployment files for services'() {
        setup:
        createUntarBuildFile(buildFile, "java-service", "service", "foo-service")

        when:
        runTasksSuccessfully(':configTar', ':untar')

        then:
        def files = new File(projectDir, 'dist/foo-service-0.0.1/').list()
        files.length == 2
        files.contains('deployment')
        def manifest = new File(projectDir, 'dist/foo-service-0.0.1/deployment/manifest.yml').text
        manifest.contains('service.v1')
        fileExists('dist/foo-service-0.0.1/service/bin/launcher-static.yml')
    }

    def 'configTar task contains the necessary deployment files for assets'() {
        setup:
        createUntarBuildFile(buildFile, "asset", "asset", "foo-asset")

        when:
        runTasksSuccessfully(':configTar', ':untar')

        then:
        def files = new File(projectDir, 'dist/foo-asset-0.0.1/').list()
        files.length == 1
        files.contains('deployment')
        def manifest = new File(projectDir, 'dist/foo-asset-0.0.1/deployment/manifest.yml').text
        manifest.contains('asset.v1')
    }

    def 'configTar task support configuration.ymls being generated to a non-standard location'() {
        setup:
        createUntarBuildFile(buildFile, "asset", "asset", "foo-asset")

        // language=Gradle
        buildFile << '''
            task createConfigurationYml {
                outputs.file('build/some-place/configuration.yml')
                
                doFirst {
                    file('build/some-place/configuration.yml').text = 'custom: yml'
                }
            }

            distribution {
                configurationYml.fileProvider(tasks.named('createConfigurationYml').map { it.outputs.files.singleFile }) 
            }
        '''.stripIndent(true)

        when:
        runTasksSuccessfully(':configTar', ':untar')

        then:
        def configuration = new File(projectDir, 'dist/foo-asset-0.0.1/deployment/configuration.yml').text
        configuration.contains('custom: yml')
    }

    def 'errors out if the custom configuration.yml location is not a file called configuration.yml'() {
        setup:
        createUntarBuildFile(buildFile, "asset", "asset", "foo-asset")

        // language=Gradle
        buildFile << '''
            task createConfigurationYml {
                outputs.file('build/some-place/something-else.yml')
                
                doFirst {
                    file('build/some-place/something-else.yml').text = 'custom: yml'
                }
            }

            distribution {
                configurationYml.fileProvider(tasks.named('createConfigurationYml').map { it.outputs.files.singleFile }) 
            }
        '''.stripIndent(true)

        when:
        def failureMessage = Throwables.getRootCause(runTasksWithFailure(':configTar', ':untar').failure).message

        then:
        failureMessage.contains('must be called configuration.yml')
    }

    private static createUntarBuildFile(buildFile, pluginType, artifactType, name) {
        buildFile << """
            apply plugin: 'com.palantir.sls-${pluginType}-distribution'
            repositories {
                mavenCentral()
            }
            distribution {
                serviceName '${name}'
                if ('${artifactType}' == 'service') {
                    mainClass 'main.Main'
                }
            }

            version "0.0.1"
            project.group = 'service-group'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("\${buildDir}/distributions/${name}-0.0.1.${artifactType}.config.tgz"))
                into "\${projectDir}/dist"
                dependsOn configTar
            }
        """.stripIndent()
    }
}
