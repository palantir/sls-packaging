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

package com.palantir.gradle.dist.artifacts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.GradleProject;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class ManifestOciArtifactsPluginTests {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @BeforeEach
    void before(RootProject consumer, SubProject producer) {
        consumer.buildGradle().plugins().add("com.palantir.sls-java-service-distribution");
        consumer.buildGradle().append("""
            project.version = '1.0.0'
            distribution {
                serviceName 'consumer-service'
                serviceGroup 'service-group'
            }
            """);

        producer.buildGradle().append("""
            import org.gradle.api.attributes.Usage

            group = 'com.example'
            version = '2.0.0'

            configurations.consumable('manifestOciArtifactsElements') {
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, '%s'))
                }
            }
            """, ManifestOciArtifacts.USAGE);
    }

    @Test
    void adds_all_published_coordinates_from_a_project_dependency(
            GradleInvoker gradle, RootProject consumer, SubProject producer) throws IOException {
        consumer.buildGradle().append("""
            distribution {
                manifestOciArtifacts project(':producer')
            }
            """);
        addImage(producer, "image-a", "registry.example.com/group/image-a:1.0.0", true);
        addImage(producer, "image-b", "registry.example.com/group/image-b:1.0.0", true);

        gradle.withArgs("createManifest").buildsSuccessfully();

        assertThat(manifestArtifacts(consumer))
                .containsExactlyInAnyOrder(
                        JsonArtifactLocator.from("oci", "registry.example.com/group/image-a:1.0.0"),
                        JsonArtifactLocator.from("oci", "registry.example.com/group/image-b:1.0.0"));
    }

    @Test
    void excludes_coordinates_not_marked_for_publication(
            GradleInvoker gradle, RootProject consumer, SubProject producer) throws IOException {
        consumer.buildGradle().append("""
            distribution {
                manifestOciArtifacts project(':producer')
            }
            """);
        addImage(producer, "published", "registry.example.com/group/published:1.0.0", true);
        addImage(producer, "unpublished", "registry.example.com/group/unpublished:1.0.0", false);

        gradle.withArgs("createManifest").buildsSuccessfully();

        assertThat(manifestArtifacts(consumer))
                .containsExactly(JsonArtifactLocator.from("oci", "registry.example.com/group/published:1.0.0"));
    }

    @Test
    void selects_an_artifact_by_name(GradleInvoker gradle, RootProject consumer, SubProject producer)
            throws IOException {
        consumer.buildGradle().append("""
            distribution {
                manifestOciArtifacts project(':producer'), 'image-a'
            }
            """);
        addImage(producer, "image-a", "registry.example.com/group/image-a:1.0.0", true);
        addImage(producer, "image-b", "registry.example.com/group/image-b:1.0.0", true);

        gradle.withArgs("createManifest").buildsSuccessfully();

        assertThat(manifestArtifacts(consumer))
                .containsExactly(JsonArtifactLocator.from("oci", "registry.example.com/group/image-a:1.0.0"));
    }

    @Test
    void combines_direct_and_resolved_artifacts(GradleInvoker gradle, RootProject consumer, SubProject producer)
            throws IOException {
        consumer.buildGradle().append("""
            distribution {
                manifestOciArtifacts project(':producer')
                artifact {
                    type = 'direct'
                    uri = 'https://example.com/direct-artifact'
                }
            }
            """);
        addImage(producer, "image-a", "registry.example.com/group/image-a:1.0.0", true);

        gradle.withArgs("createManifest").buildsSuccessfully();

        assertThat(manifestArtifacts(consumer))
                .containsExactlyInAnyOrder(
                        JsonArtifactLocator.from("direct", "https://example.com/direct-artifact"),
                        JsonArtifactLocator.from("oci", "registry.example.com/group/image-a:1.0.0"));
    }

    @Test
    void runs_the_coordinate_producer_through_artifact_dependency_inference(
            GradleInvoker gradle, RootProject consumer, SubProject producer) throws IOException {
        consumer.buildGradle().append("""
            distribution {
                manifestOciArtifacts project(':producer')
            }
            """);
        addImageBuiltByTask(producer, "generateCoordinates", "image-a", "registry.example.com/group/image-a:1.0.0");

        InvocationResult result = gradle.withArgs("createManifest").buildsSuccessfully();

        result.assertThat().task(":producer:generateCoordinates").succeeded();
        assertThat(manifestArtifacts(consumer))
                .containsExactly(JsonArtifactLocator.from("oci", "registry.example.com/group/image-a:1.0.0"));
    }

    @Test
    void does_not_realize_the_resolvable_configuration_for_an_unrelated_task(
            GradleInvoker gradle, RootProject consumer) {
        consumer.buildGradle().append("""
            configurations.named('%s').configure {
                throw new GradleException('OCI artifact configuration was realized')
            }
            """, ManifestOciArtifacts.RESOLVABLE_CONFIGURATION_NAME);

        gradle.withArgs("help").buildsSuccessfully();
    }

    @Test
    void applies_distribution_artifact_configuration_to_resolved_artifacts(
            GradleInvoker gradle, RootProject consumer, SubProject producer) throws IOException {
        consumer.buildGradle().append("""
            distribution {
                manifestOciArtifacts project(':producer')
            }
            """);
        addImage(producer, "image-a", "registry.example.com/group/image-a:1.0.0", true);
        consumer.buildGradle().append("""
            distribution.artifacts.configureEach { artifact ->
                artifact.uri.set(artifact.uri.get().replace('registry.example.com', 'mirror.example.com'))
            }
            """);

        gradle.withArgs("createManifest").buildsSuccessfully();

        assertThat(manifestArtifacts(consumer))
                .containsExactly(JsonArtifactLocator.from("oci", "mirror.example.com/group/image-a:1.0.0"));
    }

    @Test
    void fails_when_all_coordinates_are_unpublished(GradleInvoker gradle, RootProject consumer, SubProject producer) {
        consumer.buildGradle().append("""
            distribution {
                manifestOciArtifacts project(':producer')
            }
            """);
        addImage(producer, "unpublished", "registry.example.com/group/unpublished:1.0.0", false);

        InvocationResult failure = gradle.withArgs("createManifest").buildsWithFailure();

        assertThat(failure.output())
                .contains("No published OCI artifact coordinates")
                .contains(ManifestOciArtifacts.CONFIGURATION_NAME);
    }

    @Test
    void fails_when_a_dependency_exposes_no_matching_coordinates_variant(
            GradleInvoker gradle, RootProject consumer, SubProject producer) {
        consumer.buildGradle().append("""
            distribution {
                manifestOciArtifacts project(':producer')
            }
            """);
        producer.buildGradle().append("""
            configurations.manifestOciArtifactsElements.attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'some-other-usage'))
            }
            """);

        InvocationResult failure = gradle.withArgs("createManifest").buildsWithFailure();

        assertThat(failure.output())
                .contains("No published OCI artifact coordinates")
                .contains(ManifestOciArtifacts.USAGE);
    }

    private static void addImage(SubProject producer, String name, String uri, boolean publish) {
        producer.buildGradle().append("""
            configurations.manifestOciArtifactsElements.outgoing.artifact(
                    layout.buildDirectory.file("coords/%1$s.json").get().asFile.tap {
                        it.parentFile.mkdirs()
                        it.text = '{"type":"oci","uri":"%2$s","publish":%3$b}'
                    })
            """, name, uri, publish);
    }

    private static void addImageBuiltByTask(SubProject producer, String taskName, String name, String uri) {
        producer.buildGradle().append("""
            def %1$s = tasks.register('%1$s') {
                def outputFile = layout.buildDirectory.file('coords/%2$s.json')
                outputs.file(outputFile)
                doLast {
                    def file = outputFile.get().asFile
                    file.parentFile.mkdirs()
                    file.text = '{"type":"oci","uri":"%3$s","publish":true}'
                }
            }
            configurations.manifestOciArtifactsElements.outgoing.artifact(%1$s.map {
                it.outputs.files.singleFile
            }) {
                builtBy %1$s
            }
            """, taskName, name, uri);
    }

    private static List<JsonArtifactLocator> manifestArtifacts(GradleProject project) throws IOException {
        Manifest manifest =
                YAML.readValue(project.file("build/deployment/manifest.yml").text(), Manifest.class);
        return manifest.extensions().artifacts();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Manifest(Extensions extensions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Extensions(List<JsonArtifactLocator> artifacts) {}
}
