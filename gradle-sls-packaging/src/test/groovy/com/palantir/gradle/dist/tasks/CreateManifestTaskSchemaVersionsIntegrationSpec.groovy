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

import com.palantir.gradle.dist.ObjectMappers
import com.palantir.gradle.dist.pdeps.ResolveProductDependenciesIntegrationSpec
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
    """.stripIndent()

    def setup() {
        buildFile << """
            apply plugin: 'com.palantir.sls-java-service-distribution'

            project.version = '1.0.0'

            distribution {
                serviceName "serviceName"
                serviceGroup "serviceGroup"
            }
        """.stripIndent()
    }

    def 'fails if lockfile is not up to date'() {
        buildFile << """
        distribution {
            ${SCHEMA}
        }
        """.stripIndent()

        file('schema-versions.lock').text = """\
        ---
        comment: "Run ./gradlew --write-locks to regenerate this file"
        schemaMigrations:
        - type: "offline"
          from: 52
        version: 1
        """.stripIndent()

        when:
        def buildResult = runTasksWithFailure(':createManifest')

        then:
        buildResult.getStandardError().contains(
                "schema-versions.lock is out of date, please run `./gradlew createManifest --write-locks` to update it")
    }

    def 'fails if unexpected lockfile exists'() {
        runTasksSuccessfully('createManifest') // ensure task is run once
        def result = runTasksSuccessfully('createManifest')
        result.wasUpToDate(':createManifest')

        when:
        file('schema-versions.lock') << '\nthis should not be here'

        then:
        runTasksWithFailure('createManifest')
    }

    def 'fails if lock file disappears'() {
        buildFile << """
        distribution {
            ${SCHEMA}
        }
        """.stripIndent()

        file('schema-versions.lock').text = """\
        ---
        comment: "Run ./gradlew --write-locks to regenerate this file"
        schemaMigrations:
        - type: "offline"
          from: 53
        version: 1
        """.stripIndent()

        runTasksSuccessfully('createManifest') // ensure task is run once
        runTasksSuccessfully('createManifest')

        when:
        file('schema-versions.lock').delete()

        then:
        runTasksWithFailure('createManifest')
    }

    def 'fails if lockfile has changed contents'() {
        buildFile << """
        distribution {
            ${SCHEMA}
        }
        """.stripIndent()

        file('schema-versions.lock').text = """\
        ---
        comment: "Run ./gradlew --write-locks to regenerate this file"
        schemaMigrations:
        - type: "offline"
          from: 53
        version: 1
        """.stripIndent()

        runTasksSuccessfully('createManifest') // ensure task is run once
        runTasksSuccessfully('createManifest')

        when:
        file('schema-versions.lock') << '\nthis should not be here'

        then:
        runTasksWithFailure('createManifest')
    }

    @Unroll
    def 'writes locks when #writeLocksTask is on the command line'() {
        buildFile << """
        distribution {
            ${SCHEMA}
        }
        """.stripIndent()

        when:
        def buildResult = runTasksSuccessfully(writeLocksTask)

        then:
        buildResult.wasExecuted(':createManifest')
        file('schema-versions.lock').text == """\
        ---
        comment: "Run ./gradlew --write-locks to regenerate this file"
        schemaMigrations:
        - type: "offline"
          from: 53
        version: 1
        """.stripIndent()

        where:
        writeLocksTask << ['--write-locks', 'writeSchemaVersionLocks', 'wSVL']
    }

    def "check depends on createManifest"() {
        when:
        def result = runTasks(':check')

        then:
        result.wasExecuted(":createManifest")
    }
}
