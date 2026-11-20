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
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;

@GradlePluginTests
// Other sls-packaging tasks (createManifest, manifestClasspathJar) are not configuration-cache compatible on Gradle 8
@DisabledConfigurationCache
class SlsDistTarIntegrationTest {
    private static final String PARALLEL_ON = "-P" + SlsDistTar.PARALLEL_ARCHIVING_PROPERTY + "=true";
    private static final String PARALLEL_OFF = "-P" + SlsDistTar.PARALLEL_ARCHIVING_PROPERTY + "=false";
    private static final String PARALLEL_LOG_LINE = "on a worker thread with parallel gzip compression";
    private static final String SERVICE_ARCHIVE = "distributions/service-name-0.0.1.sls.tgz";

    @Test
    void parallel_service_dist_tar_has_identical_contents_to_gradle_tar(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        setUpServiceDistribution(rootProject);
        useReproducibleTimestamps(rootProject);

        assertParallelArchiveIsIdenticalToGradleTar(gradle, rootProject, SERVICE_ARCHIVE);
    }

    @Test
    void parallel_dist_tar_applies_content_filters_like_gradle_tar(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        setUpServiceDistribution(rootProject);
        useReproducibleTimestamps(rootProject);
        rootProject.file("var/conf/templated.yml").overwrite("greeting: ${greeting}\n");
        rootProject.file("service/filtered.txt").overwrite("shout this\n");
        rootProject.buildGradle().append("""
            tasks.named('distTar') {
                filesMatching('**/templated.yml') {
                    expand(greeting: 'hello from expand')
                }
                filesMatching('**/filtered.txt') {
                    filter { line -> line.toUpperCase() + ' AND MAKE IT LONGER' }
                }
            }
            """);

        assertParallelArchiveIsIdenticalToGradleTar(gradle, rootProject, SERVICE_ARCHIVE);

        String parallelTar = new String(
                gunzip(Files.readAllBytes(rootProject.buildDir().path().resolve(SERVICE_ARCHIVE))),
                StandardCharsets.ISO_8859_1);
        assertThat(parallelTar).contains("greeting: hello from expand").contains("SHOUT THIS AND MAKE IT LONGER");
    }

    @Test
    void parallel_asset_dist_tar_has_identical_contents_to_gradle_tar(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        rootProject.buildGradle().plugins().add("com.palantir.sls-asset-distribution");
        rootProject.buildGradle().append("""
            version '0.0.1'
            distribution {
                serviceName 'asset-name'
                assets 'static', 'web'
            }
            """);
        rootProject.file("static/index.html").overwrite("<html></html>\n");
        rootProject.file(longPath("static/") + "app.js").overwrite("console.log(1)\n");
        useReproducibleTimestamps(rootProject);

        assertParallelArchiveIsIdenticalToGradleTar(gradle, rootProject, "distributions/asset-name-0.0.1.sls.tgz");
    }

    @Test
    void parallel_dist_tar_preserves_file_timestamps_when_configured(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        setUpServiceDistribution(rootProject);
        rootProject.buildGradle().append("""
            tasks.named('distTar') {
                preserveFileTimestamps = true
            }
            """);
        Path archive = rootProject.buildDir().path().resolve(SERVICE_ARCHIVE);

        gradle.withArgs(":distTar", PARALLEL_OFF).buildsSuccessfully();
        List<String> gradleEntries = describeEntries(Files.readAllBytes(archive));
        gradle.withArgs(":distTar", PARALLEL_ON).buildsSuccessfully();
        List<String> parallelEntries = describeEntries(Files.readAllBytes(archive));

        assertThat(parallelEntries).isEqualTo(gradleEntries);
        assertThat(gradleEntries)
                .as("real files should carry their on-disk timestamp rather than the reproducible constant")
                .anySatisfy(entry ->
                        assertThat(entry).contains("/var/conf/service.yml").doesNotContain("mtime=86400 "));
    }

    @Test
    void dist_tar_uses_gradle_tar_by_default(GradleInvoker gradle, RootProject rootProject) throws IOException {
        setUpServiceDistribution(rootProject);
        useReproducibleTimestamps(rootProject);
        Path archive = rootProject.buildDir().path().resolve(SERVICE_ARCHIVE);

        InvocationResult byDefault = gradle.withArgs(":distTar", "--info").buildsSuccessfully();
        byte[] defaultArchive = Files.readAllBytes(archive);
        gradle.withArgs(":distTar", "--rerun", PARALLEL_OFF).buildsSuccessfully();

        assertThat(byDefault).output().doesNotContain(PARALLEL_LOG_LINE);
        assertThat(defaultArchive).isEqualTo(Files.readAllBytes(archive));
    }

    private static void assertParallelArchiveIsIdenticalToGradleTar(
            GradleInvoker gradle, RootProject rootProject, String archivePath) throws IOException {
        Path archive = rootProject.buildDir().path().resolve(archivePath);

        gradle.withArgs(":distTar", PARALLEL_OFF).buildsSuccessfully();
        byte[] gradleTar = gunzip(Files.readAllBytes(archive));

        InvocationResult result =
                gradle.withArgs(":distTar", PARALLEL_ON, "--info").buildsSuccessfully();
        byte[] parallelTar = gunzip(Files.readAllBytes(archive));

        assertThat(result).task(":distTar").succeeded();
        assertThat(result).output().contains(PARALLEL_LOG_LINE);
        assertThat(parallelTar).isEqualTo(gradleTar);
    }

    private static void setUpServiceDistribution(RootProject rootProject) throws IOException {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");
        rootProject.buildGradle().append("""
            project.group = 'service-group'
            repositories {
                mavenCentral()
            }
            version '0.0.1'
            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
            }
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {
                public static void main(String[] args) {}
            }
            """);
        rootProject.file("var/conf/service.yml").overwrite("key: value\n");
        // Needs GNU long-name records in the tar header
        rootProject.file(longPath("service/") + "long-file-name.txt").overwrite("contents\n");
        rootProject.directory("service/empty-directory").createDirectories();
        // Larger than one compression block, so the archive spans several independently compressed blocks
        byte[] incompressible = new byte[3 * 1024 * 1024 + 123];
        new Random(42).nextBytes(incompressible);
        Path blob = rootProject.path().resolve("service/data/blob.bin");
        Files.createDirectories(blob.getParent());
        Files.write(blob, incompressible);
    }

    private static void useReproducibleTimestamps(RootProject rootProject) {
        // Otherwise directories that only exist in the copy spec get the current time, so no two builds are identical
        rootProject.buildGradle().append("""
            tasks.named('distTar') {
                preserveFileTimestamps = false
            }
            """);
    }

    private static String longPath(String prefix) {
        return prefix + "nested-directory-name/".repeat(6);
    }

    /**
     * Describes each entry in order. Directory timestamps are omitted: with preserved timestamps, directories that only
     * exist in the copy spec are stamped with the current time, which differs between any two builds.
     */
    private static List<String> describeEntries(byte[] archive) throws IOException {
        List<String> entries = new ArrayList<>();
        try (TarArchiveInputStream tar =
                new TarArchiveInputStream(new GZIPInputStream(new ByteArrayInputStream(archive)))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                String mtime = entry.isDirectory()
                        ? ""
                        : " mtime=" + entry.getLastModifiedTime().toInstant().getEpochSecond() + " ";
                String contents = entry.isDirectory() ? "" : sha256(tar.readAllBytes());
                entries.add(String.format(
                        "%s mode=%o size=%d%s%s", entry.getName(), entry.getMode(), entry.getSize(), mtime, contents));
            }
        }
        return entries;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return input.readAllBytes();
        }
    }
}
