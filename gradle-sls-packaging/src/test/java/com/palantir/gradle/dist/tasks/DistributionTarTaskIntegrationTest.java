/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@GradlePluginTests
class DistributionTarTaskIntegrationTest {
    @BeforeEach
    void setup(RootProject project) {
        project.buildGradle().plugins().addWithoutApply("com.palantir.sls-asset-distribution");
        project.buildGradle().append("""
            tasks.register('archive', com.palantir.gradle.dist.tasks.DistributionTarTask) {
                compression = Compression.GZIP
                archiveFileName = 'example.sls.tgz'
                destinationDirectory = layout.buildDirectory.dir('distributions')
                preserveFileTimestamps = false
                reproducibleFileOrder = true
                usePigz.set(providers.gradleProperty('usePigz').map { it.toBoolean() }.orElse(false))
                pigzThreads.set(providers.gradleProperty('threads').map { it.toInteger() }.orElse(4))
                pigzExecutable.set(providers.gradleProperty('executable').orElse('pigz'))
                from('input') {
                    into('example-1.0')
                    filePermissions { unix('rwxr-xr-x') }
                }
            }
            """);
        project.file("input/start script.sh").overwrite("#!/bin/sh\necho hello\n");
        project.file("input/nested/" + "long-name".repeat(15)).overwrite("long path contents");
    }

    @Test
    void default_compression_does_not_require_pigz(GradleInvoker gradle, RootProject project) throws IOException {
        gradle.withArgs("archive", "-Pexecutable=missing-pigz").buildsSuccessfully();
        assertThat(uncompressedArchive(project)).isNotEmpty();
    }

    @Test
    void pigz_preserves_tar_contents_and_metadata_and_supports_incremental_builds(
            GradleInvoker gradle, RootProject project) throws IOException {
        gradle.withArgs("archive").buildsSuccessfully();
        byte[] standardTar = uncompressedArchive(project);

        InvocationResult enabled = gradle.withArgs("archive", "-PusePigz=true").buildsSuccessfully();
        assertThat(enabled).task(":archive").succeeded();
        assertThat(uncompressedArchive(project)).isEqualTo(standardTar);
        byte[] compressed = Files.readAllBytes(archive(project));

        InvocationResult unchanged =
                gradle.withArgs("archive", "-PusePigz=true").buildsSuccessfully();
        assertThat(unchanged).task(":archive").upToDate();

        InvocationResult threadsChanged =
                gradle.withArgs("archive", "-PusePigz=true", "-Pthreads=2").buildsSuccessfully();
        assertThat(threadsChanged).task(":archive").succeeded();
        assertThat(uncompressedArchive(project)).isEqualTo(standardTar);

        gradle.withArgs("archive", "-PusePigz=true", "--rerun-tasks").buildsSuccessfully();
        assertThat(Files.readAllBytes(archive(project))).isEqualTo(compressed);

        InvocationResult disabled = gradle.withArgs("archive").buildsSuccessfully();
        assertThat(disabled).task(":archive").succeeded();
        assertThat(uncompressedArchive(project)).isEqualTo(standardTar);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejects_invalid_thread_count(int threads, GradleInvoker gradle) {
        InvocationResult result = gradle.withArgs("archive", "-PusePigz=true", "-Pthreads=" + threads)
                .buildsWithFailure();
        assertThat(result).output().contains("pigzThreads must be greater than zero");
    }

    @Test
    void rejects_non_gzip_compression(GradleInvoker gradle, RootProject project) {
        project.buildGradle().append("tasks.named('archive') { compression = Compression.BZIP2 }");
        InvocationResult result = gradle.withArgs("archive", "-PusePigz=true").buildsWithFailure();
        assertThat(result).output().contains("usePigz requires distTar compression to be GZIP");
    }

    @Test
    void missing_compressor_does_not_leave_a_plain_tar_and_can_be_retried(GradleInvoker gradle, RootProject project)
            throws IOException {
        InvocationResult failed = gradle.withArgs("archive", "-PusePigz=true", "-Pexecutable=missing-pigz")
                .buildsWithFailure();
        assertThat(failed).output().contains("Failed to create distribution using pigz");
        assertThat(archive(project)).doesNotExist();

        gradle.withArgs("archive", "-PusePigz=true").buildsSuccessfully();
        assertThat(uncompressedArchive(project)).isNotEmpty();
    }

    @Test
    void failed_compressor_does_not_leave_partial_output(GradleInvoker gradle, RootProject project) throws IOException {
        Path compressor = project.file("failing pigz")
                .overwrite("#!/bin/sh\nprintf partial-output\nexit 17\n")
                .path();
        assertThat(compressor.toFile().setExecutable(true)).isTrue();
        InvocationResult result = gradle.withArgs("archive", "-PusePigz=true", "-Pexecutable=" + compressor)
                .buildsWithFailure();
        assertThat(result).output().contains("Failed to create distribution using pigz");
        assertThat(archive(project)).doesNotExist();
    }

    private static Path archive(RootProject project) {
        return project.buildDir().file("distributions/example.sls.tgz").path();
    }

    private static byte[] uncompressedArchive(RootProject project) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(archive(project)))) {
            return input.readAllBytes();
        }
    }
}
