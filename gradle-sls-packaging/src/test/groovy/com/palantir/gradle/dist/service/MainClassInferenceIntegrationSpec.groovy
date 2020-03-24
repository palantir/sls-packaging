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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.palantir.gradle.dist.GradleIntegrationSpec
import com.palantir.gradle.dist.service.tasks.LaunchConfigTask
import org.gradle.testkit.runner.UnexpectedBuildFailure

class MainClassInferenceIntegrationSpec extends GradleIntegrationSpec {
    private static final OBJECT_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new GuavaModule())

    def setup() {
        buildFile << '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }
            
            repositories {
                jcenter()
                maven { url "https://palantir.bintray.com/releases" }
            }

            version '0.0.1'
        '''.stripIndent()
    }

    def 'infers main class correctly'() {
        given:
        buildFile << """
            distribution {
                serviceName 'service-name'
                gc 'hybrid'
            }
            
            // Force run to be realized eagerly
            task Foo {} 
            tasks.run.dependsOn Foo
            ${unTarTask('service-name')}
        """.stripIndent()
        file('src/main/java/test/Test.java') << mainClass('Test')

        when:
        runTasks(':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                file('dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfigTask.LaunchConfig)
        actualStaticConfig.mainClass() == "test.Test"
    }

    def 'fails to infer main class if there are many'() {
        given:
        buildFile << '''
            distribution {
                serviceName 'service-name'
                gc 'hybrid'
            }
        '''.stripIndent()
        file('src/main/java/test/Test.java') << mainClass('Test')
        file('src/main/java/test/Test2.java') << mainClass('Test2')

        when:
        runTasks(':distTar')

        then:
        def error = thrown(UnexpectedBuildFailure)
        error.message.contains('Expecting to find exactly one main method, however we found 2')
    }

    def 'allows users to override main class if there are many'() {
        given:
        buildFile << """
            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                gc 'hybrid'
            }
            ${unTarTask('service-name')}
        """.stripIndent()
        file('src/main/java/test/Test.java') << mainClass('Test')
        file('src/main/java/test/Test2.java') << mainClass('Test2')

        when:
        runTasks(':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                file('dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfigTask.LaunchConfig)
        actualStaticConfig.mainClass() == "test.Test"
    }

    def unTarTask(String serviceName) {
        return """
        // most convenient way to untar the dist is to use gradle
        task untar(type: Copy) {
            from tarTree(resources.gzip("build/distributions/${serviceName}-0.0.1.sls.tgz"))
            into "dist"
            dependsOn distTar
        }
        """.stripIndent()
    }

    static def mainClass(String className) {
        return """
        package test;
        public class ${className} {
            public static void main(String[] args) {
                while(true);
            }
        }
        """.stripIndent()
    }

}
