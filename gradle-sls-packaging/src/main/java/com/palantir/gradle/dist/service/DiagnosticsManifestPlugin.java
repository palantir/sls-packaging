/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.dist.tasks.CreateManifestTask;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

public final class DiagnosticsManifestPlugin implements Plugin<Project> {
    private static final Logger log = Logging.getLogger(DiagnosticsManifestPlugin.class);

    // The 'artifactType' attribute is defined by
    // org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT.
    // Annoyingly that class is 'internal', even though the values defined in 'ArtifactTypeDefinition' are public!
    private static final Attribute<String> artifactType = Attribute.of("artifactType", String.class);
    static final String mergeDiagnosticsJson = "mergeDiagnosticsJson";

    private static final String FILE_TO_EXTRACT = "sls-manifest/diagnostics.json";
    private static final String MY_ATTRIBUTE = "extracted-" + FILE_TO_EXTRACT;

    /**
     * This plugin uses a few slightly 'advanced' gradle features:
     *
     * - "attributes" (https://docs.gradle.org/current/userguide/variant_attributes.html)
     * - "artifact transforms" (https://docs.gradle.org/current/userguide/artifact_transforms.html)
     *
     * These seem to be the new idiomatic way of doing things (and Gradle uses them _heavily_ internally, e.g. to
     * delineate the 'api' vs 'implementation' stuff in the {@link org.gradle.api.plugins.JavaPlugin}).
     * They let us declaratively express things in a _really_ nice way,
     * with beautiful granular caching etc. See https://docs.gradle.org/current/userguide/variant_model.html for some
     * helpful diagrams. Also try running `./gradlew outgoingVariants` on a project to visualize what's going on.
     *
     * We define a couple of {@link TransformAction}s so that when we define that we want just an extracted file, and
     * gradle has all these jars and resources, it can use the right TransformAction to bridge the gap.
     */
    @Override
    @SuppressWarnings("RawTypes")
    public void apply(Project project) {
        configureExternalDependencyTransform(project);
        configureProjectDependencyTransform(project);

        Configuration consumableRuntimeConfiguration = createConsumableRuntimeConfiguration(project);
        ArtifactView attributeSpecificArtifactView = consumableRuntimeConfiguration
                .getIncoming()
                .artifactView(v -> {
                    v.getAttributes().attribute(artifactType, MY_ATTRIBUTE);
                    v.getAttributes()
                            .attribute(
                                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                    project.getObjects().named(LibraryElements.class, MY_ATTRIBUTE));
                });

        TaskProvider<MergeDiagnosticsJsonTask> mergeDiagnosticsTask = project.getTasks()
                .register(mergeDiagnosticsJson, MergeDiagnosticsJsonTask.class, task -> {
                    task.getClasspath()
                            .from(attributeSpecificArtifactView.getArtifacts().getArtifactFiles());
                    task.getOutputJsonFile()
                            .set(project.getLayout().getBuildDirectory().file(task.getName() + ".json"));
                });

        log.error("TOMP: 0");
        project.getPlugins().withId("com.palantir.sls-java-service-distribution", _plugin -> {
            log.error("TOMP: 1");
            project.getExtensions().configure(JavaServiceDistributionExtension.class, ext -> {
                log.error(
                        "TOMP 2: {}:{} with map:",
                        ext.getDistributionServiceGroup().get(),
                        ext.getDistributionServiceName().get());
                ext.getManifestExtensions().get().forEach((key, value) -> log.error("entry: {} -> {}", key, value));
                ext.setManifestExtension(
                        "diagnostics",
                        mergeDiagnosticsTask
                                .flatMap(MergeDiagnosticsJsonTask::getOutputJsonFile)
                                .map(file -> Diagnostics.parse(project, file.getAsFile())));
            });
            project.getTasks().named("createManifest", CreateManifestTask.class).configure(createManifestTask -> {
                createManifestTask.dependsOn(mergeDiagnosticsJson);
            });
        });
    }

    private static void configureExternalDependencyTransform(Project project) {
        project.getDependencies().registerTransform(ExtractFileFromJar.class, details -> {
            details.getParameters().getPathToExtract().set(FILE_TO_EXTRACT);

            details.getFrom().attribute(artifactType, ArtifactTypeDefinition.JAR_TYPE);
            details.getTo().attribute(artifactType, MY_ATTRIBUTE);

            details.getFrom()
                    .attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            details.getTo()
                    .attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            project.getObjects().named(LibraryElements.class, MY_ATTRIBUTE));
        });
    }

    private static void configureProjectDependencyTransform(Project project) {
        project.getDependencies().registerTransform(SelectSingleFile.class, details -> {
            details.getParameters().getPathToExtract().set(FILE_TO_EXTRACT);

            details.getFrom().attribute(artifactType, ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY);
            details.getTo().attribute(artifactType, MY_ATTRIBUTE);

            details.getFrom()
                    .attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            project.getObjects().named(LibraryElements.class, LibraryElements.RESOURCES));
            details.getTo()
                    .attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            project.getObjects().named(LibraryElements.class, MY_ATTRIBUTE));
        });
    }

    private static Configuration createConsumableRuntimeConfiguration(Project project) {
        Configuration consumable = project.getConfigurations().create("runtimeClasspathForDiagnostics", conf -> {
            conf.extendsFrom(project.getConfigurations().getByName("runtimeClasspath"));
            conf.setDescription("DiagnosticsManifestPlugin uses this configuration to extract single file");
            conf.setCanBeConsumed(true);
            conf.setCanBeResolved(true);
            conf.setVisible(false);
        });

        project.getDependencies().add(consumable.getName(), project);
        return consumable;
    }

    @CacheableTransform
    public abstract static class ExtractFileFromJar implements TransformAction<ExtractFileFromJar.Parameters> {
        private static final Logger log = Logging.getLogger(ExtractFileFromJar.class);

        interface Parameters extends TransformParameters {
            @Input
            Property<String> getPathToExtract();
        }

        @PathSensitive(PathSensitivity.NAME_ONLY)
        @InputArtifact
        public abstract Provider<FileSystemLocation> getInputArtifact();

        @Override
        public final void transform(TransformOutputs outputs) {
            File jarFile = getInputArtifact().get().getAsFile();
            String pathToExtract = getParameters().getPathToExtract().get();

            try (ZipFile zipFile = new ZipFile(jarFile)) {
                ZipEntry zipEntry = zipFile.getEntry(pathToExtract);
                if (zipEntry == null) {
                    log.debug("Unable to find '{}' in JAR: {}", pathToExtract, jarFile);
                    return;
                }

                try (InputStream is = zipFile.getInputStream(zipEntry)) {
                    String newFileName = com.google.common.io.Files.getNameWithoutExtension(jarFile.getName()) + "-"
                            + pathToExtract.replaceAll("/", "-");
                    File outputFile = outputs.file(newFileName);
                    Files.copy(is, outputFile.toPath());
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to extract '" + pathToExtract + "' from jar: " + jarFile, e);
            }
        }
    }

    @CacheableTransform
    public abstract static class SelectSingleFile implements TransformAction<SelectSingleFile.Parameters> {
        private static final Logger log = Logging.getLogger(SelectSingleFile.class);

        interface Parameters extends TransformParameters {
            @Input
            Property<String> getPathToExtract();
        }

        @PathSensitive(PathSensitivity.NAME_ONLY)
        @InputArtifact
        public abstract Provider<FileSystemLocation> getInputArtifact();

        @Override
        public final void transform(TransformOutputs outputs) {
            File resourcesMainDir = getInputArtifact().get().getAsFile();
            Path pathToExtract = resourcesMainDir
                    .toPath()
                    .resolve(getParameters().getPathToExtract().get());

            if (!Files.exists(pathToExtract)) {
                log.debug("Could not find '{}' in {}", pathToExtract, resourcesMainDir);
                return;
            }

            Path outputFile = outputs.file("SelectSingleFile-output").toPath();
            try {
                Files.copy(pathToExtract, outputFile);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Failed to copy '%s'", pathToExtract), e);
            }
        }
    }
}
