/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.dist.GradleTestVersions
import nebula.test.IntegrationSpec
import spock.lang.Unroll

class CreateManifestTaskSchemaVersionsIntegrationSpec extends IntegrationSpec {
    public static String SCHEMA = """
        manifestExtensions 'schema-migrations': [
            [
                'from': 53,
                'type': 'offline'
            ],
        ]
    """.stripIndent(true)

    def setup() {
        buildFile << """
            apply plugin: 'com.palantir.sls-java-service-distribution'

            project.version = '1.0.0'

            distribution {
                serviceName "serviceName"
                serviceGroup "serviceGroup"
            }
        """.stripIndent(true)
    }

    def '#gradleVersionNumber: fails if lockfile is not up to date'() {
        setup:
        gradleVersion = gradleVersionNumber
        buildFile << """
        distribution {
            ${SCHEMA}
        }
        """.stripIndent(true)

        file('schema-versions.lock').text = """\
        ---
        comment: "Run ./gradlew writeSchemaVersionLocks to regenerate this file"
        schemaMigrations:
        - type: "offline"
          from: 52
        version: 1
        """.stripIndent(true)

        when:
        def buildResult = runTasksWithFailure(':createManifest')

        then:
        buildResult.getStandardError().contains(
                "schema-versions.lock is out of date, please run `./gradlew writeSchemaVersionLocks` to update it")

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: fails if unexpected lockfile exists'() {
        setup:
        gradleVersion = gradleVersionNumber
        
        runTasksSuccessfully('createManifest') // ensure task is run once
        def result = runTasksSuccessfully('createManifest')
        result.wasUpToDate(':createManifest')

        when:
        file('schema-versions.lock') << '\nthis should not be here'

        then:
        runTasksWithFailure('createManifest')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: fails if lock file disappears'() {
        setup:
        gradleVersion = gradleVersionNumber
        buildFile << """
        distribution {
            ${SCHEMA}
        }
        """.stripIndent(true)

        file('schema-versions.lock').text = """\
        ---
        comment: "Run ./gradlew writeSchemaVersionLocks to regenerate this file"
        schemaMigrations:
        - type: "offline"
          from: 53
        version: 1
        """.stripIndent(true)

        runTasksSuccessfully('createManifest') // ensure task is run once
        runTasksSuccessfully('createManifest')

        when:
        file('schema-versions.lock').delete()

        then:
        runTasksWithFailure('createManifest')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: fails if lockfile has changed contents'() {
        setup:
        gradleVersion = gradleVersionNumber
        buildFile << """
        distribution {
            ${SCHEMA}
        }
        """.stripIndent(true)

        file('schema-versions.lock').text = """\
        ---
        comment: "Run ./gradlew writeSchemaVersionLocks to regenerate this file"
        schemaMigrations:
        - type: "offline"
          from: 53
        version: 1
        """.stripIndent(true)

        runTasksSuccessfully('createManifest') // ensure task is run once
        runTasksSuccessfully('createManifest')

        when:
        file('schema-versions.lock') << '\nthis should not be here'

        then:
        runTasksWithFailure('createManifest')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    @Unroll
    def '#gradleVersionNumber: writes locks when #writeLocksTask is on the command line'() {
        setup:
        gradleVersion = gradleVersionNumber
        buildFile << """
        distribution {
            ${SCHEMA}
        }
        """.stripIndent(true)

        when:
        def buildResult = runTasksSuccessfully(writeLocksTask)

        then:
        buildResult.wasExecuted(':createManifest')
        file('schema-versions.lock').text == """\
        ---
        comment: "Run ./gradlew writeSchemaVersionLocks to regenerate this file"
        schemaMigrations:
        - type: "offline"
          from: 53
        version: 1
        """.stripIndent(true)

        where:
        [gradleVersionNumber, writeLocksTask] << [
                GradleTestVersions.GRADLE_VERSIONS,
                ['--write-locks', 'writeSchemaVersionLocks', 'wSVL']
        ].combinations()
    }

    def "#gradleVersionNumber: check depends on createManifest"() {
        setup:
        gradleVersion = gradleVersionNumber
        
        when:
        def result = runTasks(':check')

        then:
        result.wasExecuted(":createManifest")

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }
}
