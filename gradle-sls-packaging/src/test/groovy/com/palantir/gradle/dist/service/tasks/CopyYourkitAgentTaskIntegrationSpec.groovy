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

package com.palantir.gradle.dist.service.tasks


import nebula.test.functional.ExecutionResult

class CopyYourkitAgentTaskIntegrationSpec extends GradleIntegrationSpec {

    def 'copyYourkitAgent task is up to date if already run'() {
        setup:
        createUntarBuildFile(buildFile)

        when:
        ExecutionResult buildResult = runTasks(':copyYourkitAgent')

        then:
        buildResult.wasExecuted(':copyYourkitAgent')

        when:
        buildResult = runTasks(':copyYourkitAgent')

        then:
        buildResult.wasUpToDate(':copyYourkitAgent')
    }

    def 'Build produces libyjpagent file and yourkit license'() {
        given:
        createUntarBuildFile(buildFile)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        file('dist/service-name-0.0.1').exists()
        file('dist/service-name-0.0.1/service/lib/linux-x86-64/libyjpagent.so').exists()
        file('dist/service-name-0.0.1/service/lib/linux-x86-64/libyjpagent.so').getBytes().length > 0
        file('dist/service-name-0.0.1/service/lib/linux-x86-64/yourkit-license-redist.txt').exists()
        file('dist/service-name-0.0.1/service/lib/linux-x86-64/yourkit-license-redist.txt').getBytes().length > 0
    }

    private static createUntarBuildFile(buildFile) {
        buildFile << '''
            plugins {
                id 'java'
            }
            apply plugin: 'com.palantir.sls-java-service-distribution'

            project.group = 'service-group'

            repositories {
                jcenter()
                maven { url "http://palantir.bintray.com/releases" }
            }

            version '0.0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
            }

            sourceCompatibility = '1.7'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/service-name-0.0.1.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()
    }
}
