/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class JavaServiceDistributionExtensionTest extends Specification {
    private Project project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    def 'collection modifiers are cumulative when varargs are given'() {
        given:
        def ext = new JavaServiceDistributionExtension(project)

        when:
        ext.with {
            args 'a', 'b'
            args 'c', 'd'

            checkArgs 'a', 'b'
            checkArgs 'c', 'd'

            defaultJvmOpts 'a', 'b'
            defaultJvmOpts 'c', 'd'

            excludeFromVar 'a', 'b'
            excludeFromVar 'c', 'd'

            env 'a': 'b'
            env 'c': 'd'
        }

        then:
        ext.args.get() == ['a', 'b', 'c', 'd']
        ext.checkArgs.get() == ['a', 'b', 'c', 'd']
        ext.defaultJvmOpts.get() == ['a', 'b', 'c', 'd']
        ext.excludeFromVar.get() == ['log', 'run', 'a', 'b', 'c', 'd']
        ext.env.get() == ['a': 'b', 'c': 'd']
    }

    def 'collection setters replace existing data'() {
        given:
        def ext = new JavaServiceDistributionExtension(project)

        when:
        ext.with {
            setArgs(['a', 'b'])
            setArgs(['c', 'd'])
            setCheckArgs(['a', 'b'])
            setCheckArgs(['c', 'd'])
            setDefaultJvmOpts(['a', 'b'])
            setDefaultJvmOpts(['c', 'd'])
            setExcludeFromVar(['a', 'b'])
            setExcludeFromVar(['c', 'd'])
            setEnv(['a': 'b', 'c': 'd'])
            setEnv(['foo': 'bar'])
        }

        then:
        ext.args.get() == ['c', 'd']
        ext.checkArgs.get() == ['c', 'd']
        ext.defaultJvmOpts.get() == ['c', 'd']
        ext.excludeFromVar.get() == ['c', 'd']
        ext.env.get() == ['foo': 'bar']
    }

    def 'sensible defaults' () {
        when:
        def ext = new JavaServiceDistributionExtension(project)

        then:
        ext.getAddJava8GcLogging().get() == false
        ext.getEnableManifestClasspath().get() == false
        ext.getArgs().get() == []
        ext.getCheckArgs().get() == []
        ext.getDefaultJvmOpts().get() == []
        ext.getExcludeFromVar().get() == ['log', 'run']
    }

    def 'correct java homes depending on java version' () {
        when:
        def ext = new JavaServiceDistributionExtension(project)
        project.pluginManager.apply(JavaPlugin)

        def assertJavaHomeAtVersionIs = { Object javaVersion, String javaHome ->
            project.getConvention().getPlugin(JavaPluginConvention).setTargetCompatibility(javaVersion)
            ext.getJavaHome().get() == javaHome
        }

        then:
        assertJavaHomeAtVersionIs JavaVersion.VERSION_1_6,  ''
        assertJavaHomeAtVersionIs JavaVersion.VERSION_1_7,  ''
        assertJavaHomeAtVersionIs JavaVersion.VERSION_1_8,  ''
        assertJavaHomeAtVersionIs JavaVersion.VERSION_1_9,  '$JAVA_9_HOME'
        assertJavaHomeAtVersionIs JavaVersion.VERSION_1_10, '$JAVA_10_HOME'
        assertJavaHomeAtVersionIs JavaVersion.VERSION_11,   '$JAVA_11_HOME'
        assertJavaHomeAtVersionIs JavaVersion.VERSION_12,   '$JAVA_12_HOME'
        assertJavaHomeAtVersionIs '13', '$JAVA_13_HOME'
    }
}
