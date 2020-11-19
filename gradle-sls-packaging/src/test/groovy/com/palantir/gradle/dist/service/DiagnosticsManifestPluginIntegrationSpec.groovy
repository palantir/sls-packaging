/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service


import nebula.test.IntegrationSpec

import java.nio.file.Files
import java.nio.file.Path

class DiagnosticsManifestPluginIntegrationSpec extends IntegrationSpec {

    private void enableLocalBuildCache() {
        Path localBuildCache = Files.createDirectories(projectDir.toPath().resolve("local-build-cache"))
        file("gradle.properties") << "org.gradle.caching=true"
        settingsFile << """
        buildCache {
            local {
                directory = file("${localBuildCache}")
                enabled = true
            }
        }
        """.stripIndent()
    }

    def 'detects stuff defined in current project'() {
        when:
        enableLocalBuildCache()
        buildFile << '''
        apply plugin: 'java-library'
        apply plugin: com.palantir.gradle.dist.service.DiagnosticsManifestPlugin
        
        repositories {
          mavenCentral()
        }
 
        dependencies {
          implementation 'com.fasterxml.jackson.core:jackson-databind:2.11.3'
        }
        '''
        addResource("src/main/resources/sls-manifest", "diagnostics.json", '[{"type": "foo.v1"}]')

        then:
        runTasks("mergeDiagnosticsJson", '-is')
        def outFile = new File(projectDir, "build/mergeDiagnosticsJson.json")
        assert outFile.text == """\
        [ {
          "type" : "foo.v1"
        } ]""".stripIndent()

        when:
        def output2 = runTasks("mergeDiagnosticsJson", '-is')

        then:
        output2.getStandardOutput().contains("Task :mergeDiagnosticsJson UP-TO-DATE")

        when:
        outFile.delete()
        def output3 = runTasks("mergeDiagnosticsJson", '-is')

        then:
        output3.getStandardOutput().contains("Task :mergeDiagnosticsJson FROM-CACHE")
    }

    def 'detects stuff defined in sibling projects'() {
        when:
        buildFile << '''
        subprojects {
            apply plugin: 'java-library'
            repositories {
              mavenCentral()
            }
        }
        '''

        addSubproject('my-server', '''
        apply plugin: com.palantir.gradle.dist.service.DiagnosticsManifestPlugin
        dependencies {
            implementation project(':my-project1')
            implementation project(':my-project2')
        }
        ''')
        addResource("my-server/src/main/resources/sls-manifest", "diagnostics.json", '[{"type": "foo.v1"}]')

        addSubproject('my-project1')
        addResource("my-project1/src/main/resources/sls-manifest", "diagnostics.json", '[{"type": "myproject1.v1"}]')

        addSubproject('my-project2')
        addResource("my-project2/src/main/resources/sls-manifest", "diagnostics.json", '[{"type": "myproject2.v1"}]')

        then:
        def output = runTasks("my-server:mergeDiagnosticsJson", '-is')
        println output.standardOutput
        println output.standardError
        assert new File(projectDir, "my-server/build/mergeDiagnosticsJson.json").text == """\
        [ {
          "type" : "foo.v1"
        }, {
          "type" : "myproject1.v1"
        }, {
          "type" : "myproject2.v1"
        } ]""".stripIndent()
    }
}
