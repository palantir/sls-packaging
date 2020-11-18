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

    def 'do something'() {
        when:
        buildFile << '''
        apply plugin: 'java'
        apply plugin: com.palantir.gradle.dist.service.DiagnosticsManifestPlugin
        
        repositories {
          mavenCentral()
        }
 
        dependencies {
          implementation 'com.fasterxml.jackson.core:jackson-databind:2.11.3'
//          runtimeClasspathExtracted
        }
        
        
        task doStuff  {
          doLast {
            println configurations.runtimeClasspath.files
            println configurations.runtimeClasspath.attributes.keySet()
            println configurations.runtimeClasspath.attributes.getAttribute(com.palantir.gradle.dist.service.DiagnosticsManifestPlugin.DIAGNOSTIC_JSON_EXTRACTED)
          }
        }
        '''

        then:
        def output = runTasks("foo", '-is')
        println output.standardOutput
        println output.standardError
    }
}
