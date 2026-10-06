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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@GradlePluginTests
@DisabledConfigurationCache
class DistributionCompressionIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"asset", "java-service"})
    void distribution_extension_enables_pigz_without_changing_archive_contents(
            String plugin, GradleInvoker gradle, RootProject project) throws IOException {
        project.buildGradle().plugins().add("com.palantir.sls-" + plugin + "-distribution");
        project.buildGradle().append("""
            repositories { mavenCentral() }
            version = '1.0.0'
            group = 'test'
            distribution {
                serviceName 'example'
                %s
            }
            tasks.named('distTar', Tar) {
                preserveFileTimestamps = false
                reproducibleFileOrder = true
                from('extra') { into('example-1.0.0/extra') }
            }
            """, plugin.equals("java-service") ? "mainClass 'example.Main'" : "");
        project.file("extra/payload.txt").overwrite("extra files from a consumer's CopySpec");
        gradle.withArgs("distTar").buildsSuccessfully();
        Path archive =
                project.buildDir().file("distributions/example-1.0.0.sls.tgz").path();
        byte[] standardTar = uncompress(archive);

        project.buildGradle().append("""
            distribution {
                usePigz.set(true)
                pigzThreads.set(2)
                pigzExecutable.set('pigz')
            }
            """);
        gradle.withArgs("distTar").buildsSuccessfully();
        assertThat(uncompress(archive)).isEqualTo(standardTar);
    }

    private static byte[] uncompress(Path archive) throws IOException {
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
            return input.readAllBytes();
        }
    }
}
