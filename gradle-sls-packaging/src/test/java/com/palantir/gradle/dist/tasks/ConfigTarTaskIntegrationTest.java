/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class ConfigTarTaskIntegrationTest {

    @Test
    void config_tar_task_exists_for_services(GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject, "java-service", "service", "foo-service");

        gradle.withArgs(":configTar").buildsSuccessfully();

        rootProject
                .buildDir()
                .file("distributions/foo-service-0.0.1.service.config.tgz")
                .assertThat()
                .exists();
    }

    @Test
    void config_tar_task_exists_for_assets(GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject, "asset", "asset", "foo-asset");

        gradle.withArgs(":configTar").buildsSuccessfully();

        rootProject
                .buildDir()
                .file("distributions/foo-asset-0.0.1.asset.config.tgz")
                .assertThat()
                .exists();
    }

    @Test
    void config_tar_task_contains_the_necessary_deployment_files_for_services(
            GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject, "java-service", "service", "foo-service");

        gradle.withArgs(":configTar", ":untar").buildsSuccessfully();

        Path distDir = rootProject.path().resolve("dist/foo-service-0.0.1");
        String[] files = distDir.toFile().list();
        assertThat(files).hasSize(2).contains("deployment");

        String manifest = rootProject
                .file("dist/foo-service-0.0.1/deployment/manifest.yml")
                .text();
        assertThat(manifest).contains("service.v1");

        rootProject
                .file("dist/foo-service-0.0.1/service/bin/launcher-static.yml")
                .assertThat()
                .exists();
    }

    @Test
    void config_tar_task_contains_the_necessary_deployment_files_for_assets(
            GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject, "asset", "asset", "foo-asset");

        gradle.withArgs(":configTar", ":untar").buildsSuccessfully();

        Path distDir = rootProject.path().resolve("dist/foo-asset-0.0.1");
        String[] files = distDir.toFile().list();
        assertThat(files).hasSize(1).contains("deployment");

        String manifest =
                rootProject.file("dist/foo-asset-0.0.1/deployment/manifest.yml").text();
        assertThat(manifest).contains("asset.v1");
    }

    @Test
    void config_tar_task_support_configuration_ymls_being_generated_to_a_non_standard_location(
            GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject, "asset", "asset", "foo-asset");

        rootProject.buildGradle().append("""
            task createConfigurationYml {
                outputs.file('build/some-place/configuration.yml')

                doFirst {
                    file('build/some-place/configuration.yml').text = 'custom: yml'
                }
            }

            distribution {
                configurationYml.fileProvider(tasks.named('createConfigurationYml').map { it.outputs.files.singleFile })
            }
            """);

        gradle.withArgs(":configTar", ":untar").buildsSuccessfully();

        String configuration = rootProject
                .file("dist/foo-asset-0.0.1/deployment/configuration.yml")
                .text();
        assertThat(configuration).contains("custom: yml");
    }

    @Test
    void errors_out_if_the_custom_configuration_yml_location_is_not_a_file_called_configuration_yml(
            GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject, "asset", "asset", "foo-asset");

        rootProject.buildGradle().append("""
            task createConfigurationYml {
                outputs.file('build/some-place/something-else.yml')

                doFirst {
                    file('build/some-place/something-else.yml').text = 'custom: yml'
                }
            }

            distribution {
                configurationYml.fileProvider(tasks.named('createConfigurationYml').map { it.outputs.files.singleFile })
            }
            """);

        InvocationResult result = gradle.withArgs(":configTar", ":untar").buildsWithFailure();

        assertThat(result).output().contains("must be called configuration.yml");
    }

    private void createUntarBuildFile(RootProject rootProject, String pluginType, String artifactType, String name) {
        rootProject.buildGradle().plugins().add("com.palantir.sls-" + pluginType + "-distribution");

        rootProject
                .buildGradle()
                .append("""
                    repositories {
                        mavenCentral()
                    }
                    distribution {
                        serviceName '%s'
                        %s
                    }

                    version "0.0.1"
                    project.group = 'service-group'

                    // most convenient way to untar the dist is to use gradle
                    task untar (type: Copy) {
                        from tarTree(resources.gzip("${buildDir}/distributions/%s-0.0.1.%s.config.tgz"))
                        into "${projectDir}/dist"
                        dependsOn configTar
                    }
                    """, name, artifactType.equals("service") ? "mainClass 'main.Main'" : "", name, artifactType);
    }
}
