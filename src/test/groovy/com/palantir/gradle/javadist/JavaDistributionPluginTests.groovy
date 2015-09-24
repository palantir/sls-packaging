/*
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.javadist

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import spock.lang.Specification

class JavaDistributionPluginTests extends Specification {

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    File projectDir
    File buildFile

    def 'check plugin creates scripts and tgz' () {
        given:
        buildFile << '''
            apply plugin: 'com.palantir.java-distribution'

            distribution {
                serviceName 'service-name'
                mainClass 'nebula.hello.HelloWorld'
                args 'server', 'var/conf/service.yml'
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = with('distTar').build()

        then:
        buildResult.task(':distTar').outcome == TaskOutcome.SUCCESS
        new File(projectDir, 'build/scripts/service-name').exists()
        new File(projectDir, 'build/scripts/init.sh').exists()
        new File(projectDir, 'build/deployment/manifest.yaml').exists()
        new File(projectDir, 'build/distributions/service-name.tgz').exists()
    }

    private GradleRunner with(String... tasks) {
        GradleRunner.create().withProjectDir(projectDir).withArguments(tasks)
    }

    def setup() {
        projectDir = temporaryFolder.root
        buildFile = temporaryFolder.newFile('build.gradle')

        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }

        def pluginClasspath = pluginClasspathResource.readLines()
            .collect { it.replace('\\', '\\\\') } // escape backslashes in Windows paths
            .collect { "'$it'" }
            .join(", ")

        buildFile << """
            buildscript {
                dependencies {
                    classpath files($pluginClasspath)
                }
            }
        """.stripIndent()
    }

}
