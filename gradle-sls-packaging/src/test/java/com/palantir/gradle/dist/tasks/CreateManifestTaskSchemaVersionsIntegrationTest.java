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

package com.palantir.gradle.dist.tasks;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@GradlePluginTests
class CreateManifestTaskSchemaVersionsIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
            project.version = '1.0.0'

            distribution {
                serviceName "serviceName"
                serviceGroup "serviceGroup"
            }
            """);
    }

    @Nested
    class TestDistributionSchema {

        @BeforeEach
        void setup(RootProject rootProject) {
            rootProject.buildGradle().append("""
                distribution {
                    manifestExtensions 'schema-migrations': [
                        [
                            'from': 53,
                            'type': 'offline'
                        ],
                    ]
                }
                """);
        }

        @Test
        void fails_if_lockfile_is_not_up_to_date(GradleInvoker gradle, RootProject rootProject) {
            rootProject.file("schema-versions.lock").overwrite("""
                ---
                comment: "Run ./gradlew writeSchemaVersionLocks to regenerate this file"
                schemaMigrations:
                - type: "offline"
                  from: 52
                version: 1
                """);

            InvocationResult buildResult = gradle.withArgs(":createManifest").buildsWithFailure();

            assertThat(buildResult)
                    .output()
                    .contains("schema-versions.lock is out of date, please run `./gradlew writeSchemaVersionLocks` to"
                            + " update it");
        }

        @Test
        void fails_if_lock_file_disappears(GradleInvoker gradle, RootProject rootProject) throws IOException {
            rootProject.file("schema-versions.lock").overwrite("""
                ---
                comment: "Run ./gradlew writeSchemaVersionLocks to regenerate this file"
                schemaMigrations:
                - type: "offline"
                  from: 53
                version: 1
                """);

            gradle.withArgs("createManifest").buildsSuccessfully(); // ensure task is run once
            gradle.withArgs("createManifest").buildsSuccessfully();

            Files.delete(rootProject.file("schema-versions.lock").path());

            gradle.withArgs("createManifest").buildsWithFailure();
        }

        @Test
        void fails_if_lockfile_has_changed_contents(GradleInvoker gradle, RootProject rootProject) {
            rootProject.file("schema-versions.lock").overwrite("""
                ---
                comment: "Run ./gradlew writeSchemaVersionLocks to regenerate this file"
                schemaMigrations:
                - type: "offline"
                  from: 53
                version: 1
                """);

            gradle.withArgs("createManifest").buildsSuccessfully(); // ensure task is run once
            gradle.withArgs("createManifest").buildsSuccessfully();

            rootProject.file("schema-versions.lock").append("\nthis should not be here");

            gradle.withArgs("createManifest").buildsWithFailure();
        }

        @ParameterizedTest
        @MethodSource("writeLocksTaskProvider")
        void writes_locks_when_task_is_on_the_command_line(
                String writeLocksTask, GradleInvoker gradle, RootProject rootProject) {
            InvocationResult buildResult = gradle.withArgs(writeLocksTask).buildsSuccessfully();

            assertThat(buildResult).task(":createManifest").isPresent();
            assertThat(rootProject.file("schema-versions.lock").text()).isEqualTo("""
                ---
                comment: "Run ./gradlew writeSchemaVersionLocks to regenerate this file"
                schemaMigrations:
                - type: "offline"
                  from: 53
                version: 1
                """);
        }

        private static Stream<Arguments> writeLocksTaskProvider() {
            return Stream.of(
                    Arguments.of("--write-locks"), Arguments.of("writeSchemaVersionLocks"), Arguments.of("wSVL"));
        }
    }

    @Test
    void fails_if_unexpected_lockfile_exists(GradleInvoker gradle, RootProject rootProject) {
        gradle.withArgs("createManifest").buildsSuccessfully(); // ensure task is run once
        InvocationResult result = gradle.withArgs("createManifest").buildsSuccessfully();
        assertThat(result).task(":createManifest").upToDate();

        rootProject.file("schema-versions.lock").createEmpty().append("\nthis should not be here");

        gradle.withArgs("createManifest").buildsWithFailure();
    }

    @Test
    void check_depends_on_create_manifest(GradleInvoker gradle, RootProject _rootProject) {
        InvocationResult result = gradle.withArgs(":check").buildsSuccessfully();

        assertThat(result).task(":createManifest").isPresent();
    }
}
