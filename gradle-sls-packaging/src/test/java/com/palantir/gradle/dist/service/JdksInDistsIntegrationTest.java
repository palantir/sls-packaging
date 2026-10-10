/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rauschig.jarchivelib.ArchiveFormat;
import org.rauschig.jarchivelib.ArchiverFactory;
import org.rauschig.jarchivelib.CompressionType;

@GradlePluginTests
class JdksInDistsIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.settingsGradle().rootProjectName("myService");

        rootProject.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            group = 'group'
            version = '1.0.0'
            """);

        rootProject.file("versions.lock").createEmpty();

        // language=java
        rootProject.mainSourceSet().java().writeClass("""
            package app;

            import java.nio.file.Files;
            import java.nio.file.Paths;

            public class Main {
                public static void main(String... args) {}
            }
            """);

        rootProject.file("build/fake-jdk/release").overwrite("its a jdk trust me");
    }

    @Test
    void puts_jdk_in_dist(GradleInvoker gradle, RootProject rootProject) throws IOException {
        // language=gradle
        rootProject.buildGradle().append("""
            distribution {
                javaVersion JavaVersion.VERSION_17
                jdks.put(JavaVersion.VERSION_17, fileTree('build/fake-jdk'))
            }
            """);

        gradle.withArgs("distTar").buildsSuccessfully();

        File rootDir = extractDist(rootProject);
        Path jdkDirPath = new File(rootDir, "service/myService-jdks/jdk17").toPath();
        assertThat(jdkDirPath).exists();

        Path releaseFilePath = jdkDirPath.resolve("release");
        String releaseFileText = Files.readString(releaseFilePath);
        assertThat(releaseFileText).contains("its a jdk trust me");

        Path launcherStaticPath = new File(rootDir, "service/bin/launcher-static.yml").toPath();
        String launcherStatic = Files.readString(launcherStaticPath);
        assertThat(launcherStatic)
                .contains("javaHome: \"service/myService-jdks/jdk17\"")
                .contains("  JAVA_17_HOME: \"service/myService-jdks/jdk17\"");
    }

    @Test
    void multiple_jdks_can_exist_in_the_dist(GradleInvoker gradle, RootProject rootProject) throws IOException {
        // language=gradle
        rootProject.buildGradle().append("""
            distribution {
                javaVersion JavaVersion.VERSION_17
                jdks.put(JavaVersion.VERSION_11, fileTree('build/fake-jdk'))
                jdks.put(JavaVersion.VERSION_13, fileTree('build/fake-jdk'))
                jdks.put(JavaVersion.VERSION_17, fileTree('build/fake-jdk'))
            }
            """);

        gradle.withArgs("distTar").buildsSuccessfully();

        File rootDir = extractDist(rootProject);

        Path launcherStaticPath = new File(rootDir, "service/bin/launcher-static.yml").toPath();
        assertThat(launcherStaticPath).exists();
        String launcherStatic = Files.readString(launcherStaticPath);

        assertThat(launcherStatic).contains("javaHome: \"service/myService-jdks/jdk17\"");

        for (int version : new int[] {11, 13, 17}) {
            Path jdkDirPath = new File(rootDir, "service/myService-jdks/jdk" + version).toPath();
            assertThat(jdkDirPath).exists();

            Path releaseFilePath = jdkDirPath.resolve("release");
            String releaseFileText = Files.readString(releaseFilePath);
            assertThat(releaseFileText).contains("its a jdk trust me");

            String envVarLine = "  JAVA_" + version + "_HOME: \"service/myService-jdks/jdk" + version + "\"";
            assertThat(launcherStatic).contains(envVarLine);
        }
    }

    @Test
    void does_not_force_value_of_jdks_at_configuration_time_when_task_is_evaluated(
            GradleInvoker gradle, RootProject rootProject) throws IOException {
        // language=gradle
        rootProject.buildGradle().append("""
            distribution {
                javaVersion JavaVersion.VERSION_17
                jdks.putAll(provider {
                    println('hello ' + state.isConfiguring())
                    if (state.isConfiguring()) {
                        throw new RuntimeException("Should not be called when configuring")
                    }
                    return Map.of(JavaVersion.VERSION_17, fileTree('build/fake-jdk'))
                })
            }

            // Quite a lot of internal plugins/build.gradles unfortunately get the distTar task non-lazily. An internal
            // piece of infra sets the jdks property by resolving a configuration, which cannot happen at configuration
            // time.
            tasks.getByName('distTar')
            """);

        gradle.withArgs("distTar").buildsSuccessfully();

        File rootDir = extractDist(rootProject);

        // A way of fixing this tests seems to open up the possibility of making extra unnecessary JDK repos - ensure
        // this does not happen.
        Path jdk11Path = new File(rootDir, "service/myService-jdks/jdk11").toPath();
        assertThat(jdk11Path).doesNotExist();
    }

    @Test
    void even_a_user_clearing_env_does_not_get_rid_of_java_xx_home_env_vars(
            GradleInvoker gradle, RootProject rootProject) throws IOException {
        // language=gradle
        rootProject.buildGradle().append("""
            distribution {
                javaVersion JavaVersion.VERSION_17
                jdks.put(JavaVersion.VERSION_17, fileTree('build/fake-jdk'))
                jdks.put(JavaVersion.VERSION_11, fileTree('build/fake-jdk'))

                env.empty()
            }
            """);

        gradle.withArgs("distTar").buildsSuccessfully();

        File rootDir = extractDist(rootProject);

        Path launcherStaticPath = new File(rootDir, "service/bin/launcher-static.yml").toPath();
        String launcherStatic = Files.readString(launcherStaticPath);
        assertThat(launcherStatic)
                .contains("JAVA_11_HOME: \"service/myService-jdks/jdk11\"")
                .contains("JAVA_17_HOME: \"service/myService-jdks/jdk17\"");
    }

    private File extractDist(RootProject rootProject) throws IOException {
        File slsTgz = rootProject
                .buildDir()
                .path()
                .resolve("distributions/myService-1.0.0.sls.tgz")
                .toFile();
        File extracted = new File(slsTgz.getParent(), "extracted");

        ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP).extract(slsTgz, extracted);

        return new File(extracted, "myService-1.0.0");
    }
}
