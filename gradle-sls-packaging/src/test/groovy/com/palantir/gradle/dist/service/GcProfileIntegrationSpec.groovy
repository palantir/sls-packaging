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

import com.palantir.gradle.dist.GradleIntegrationSpec
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class GcProfileIntegrationSpec extends GradleIntegrationSpec {

    static Path touchService = Paths.get("src/test/groovy/com/palantir/gradle/dist/service/ExampleTouchService.java")

    def setup() {
        File signalFile = new File(getProjectDir(), "example-touch-service-started")
        buildFile << """
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }
            
            repositories {
                maven { url "http://palantir.bintray.com/releases" }
            }

            project.version = '1.0.0'
            
            distribution {
                serviceName 'touch-service'
                serviceGroup 'com.palantir.test'
                mainClass 'com.palantir.gradle.dist.service.ExampleTouchService'
                args '${signalFile.getAbsolutePath()}'
            }
        """.stripIndent()
        Path path = projectDir.toPath().resolve("src/main/java/com/palantir/gradle/dist/service/ExampleTouchService.java")
        Files.createDirectories(path.getParent())
        Files.copy(touchService, path)
        assert signalFile.exists() == false
    }

    def 'successfully create a distribution'() {
        setup:
        buildFile << """
        distribution {
            gc 'throughput'
        }
        """.stripIndent()

        when:
        def buildResult = runTasks(':distTar')

        then:
        println buildResult.output
    }
}
