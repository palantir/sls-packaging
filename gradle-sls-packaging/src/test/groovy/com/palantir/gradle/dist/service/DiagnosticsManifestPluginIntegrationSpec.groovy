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

class DiagnosticsManifestPluginIntegrationSpec extends IntegrationSpec {

    def 'detects stuff defined in current project'() {
        given:
        buildFile << """
        apply plugin: 'java-library'
        ${applyPlugin(DiagnosticsManifestPlugin.class)}
        
        repositories {
          mavenCentral()
        }
        """.stripIndent()
        addResource("src/main/resources/sls-manifest", "diagnostics.json",
                '[{"type": "foo.v1", "docs" : "This does something", "safe" : false}]')

        when:
        runTasksSuccessfully("mergeDiagnosticsJson", '-is')

        then:
        def outFile = file("build/mergeDiagnosticsJson.json")
        outFile.text == """\
        [ {
          "type" : "foo.v1",
          "docs" : "This does something",
          "safe" : false
        } ]""".stripIndent()

        when:
        def result2 = runTasksSuccessfully("mergeDiagnosticsJson", '-is')

        then:
        result2.wasUpToDate(":mergeDiagnosticsJson")
    }

    def 'detects stuff defined in sibling projects'() {
        given:
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
        addResource("my-server/src/main/resources/sls-manifest", "diagnostics.json",
                '[{"type": "foo.v1", "docs" : "This does something"}]')

        addSubproject('my-project1')
        addResource("my-project1/src/main/resources/sls-manifest", "diagnostics.json",
                '[{"type": "myproject1.v1", "docs" : "Who knows what this does"}]')

        addSubproject('my-project2')
        addResource("my-project2/src/main/resources/sls-manifest", "diagnostics.json",
                '[{"type": "myproject2.v1", "docs" : "Click me if you dare!"}]')

        when:
        def output = runTasksSuccessfully("my-server:mergeDiagnosticsJson", '-is')

        then:
        println output.standardOutput
        println output.standardError
        file("my-server/build/mergeDiagnosticsJson.json").text == """\
        [ {
          "type" : "foo.v1",
          "docs" : "This does something"
        }, {
          "type" : "myproject1.v1",
          "docs" : "Who knows what this does"
        }, {
          "type" : "myproject2.v1",
          "docs" : "Click me if you dare!"
        } ]""".stripIndent()
    }
}
