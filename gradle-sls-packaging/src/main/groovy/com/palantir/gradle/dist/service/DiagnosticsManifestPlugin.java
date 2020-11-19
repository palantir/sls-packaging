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
import com.palantir.gradle.dist.tasks.Diagnostics;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.ArtifactAttributes;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiagnosticsManifestPlugin implements Plugin<Project> {

    /**
     * This plugin uses Gradle's Artifact Transforms to extract a single file from all the jars on the classpath
     * (https://docs.gradle.org/current/userguide/artifact_transforms.html).
     * We mark all jars with diagnosticJsonExtracted=false and then ask for a view of diagnosticJsonExtracted=true
     * and gradle figures out it can use the {@link ExtractSingleFile} transform to process the jars and extract
     * the stuff we want! Crucially, this is all cached _beautifully_.
     */
    public static final Attribute<Boolean> DIAGNOSTIC_JSON_EXTRACTED =
            Attribute.of("diagnosticJsonExtracted", Boolean.class);

    @Override
    public void apply(Project project) {
        String fileToExtract = "sls-manifest/diagnostics.json";
        String attribute = "extracted-" + fileToExtract;

        project.getDependencies().getArtifactTypes().getByName("jar", it -> {
            it.getAttributes().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);
        });

        Configuration runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");
        runtimeClasspath.getAttributes().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);

        project.getDependencies().registerTransform(ExtractSingleFile.class, details -> {
            details.getFrom().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);
            details.getFrom().attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE);
            details.getFrom()
                    .attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            project.getObjects().named(LibraryElements.class, LibraryElements.JAR));

            details.getTo().attribute(DIAGNOSTIC_JSON_EXTRACTED, true);
            details.getTo().attribute(ArtifactAttributes.ARTIFACT_FORMAT, attribute);
            details.getTo()
                    .attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            project.getObjects().named(LibraryElements.class, attribute));
            details.getParameters().getPathToExtract().set(fileToExtract);
        });

        // In order to get classes & resources from this project bundled into a jar, we make up this new
        // configuration and add a 'self' dependency.
        Configuration consumable = project.getConfigurations().create("runtimeClasspath2", conf -> {
            conf.extendsFrom(project.getConfigurations().getByName("runtimeClasspath"));
            conf.getAttributes().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);
            conf.setDescription("DiagnosticsManifestPlugin uses this configuration to extract single file");
            conf.setCanBeConsumed(true);
            conf.setCanBeResolved(true);
        });
        project.getDependencies().add(consumable.getName(), project);

        ArtifactView myView = consumable.getIncoming().artifactView(v -> {
            v.attributes(it -> {
                it.attribute(DIAGNOSTIC_JSON_EXTRACTED, true);
            });
        });

        project.getTasks().register("mergeDiagnosticsJson", MergeDiagnosticsJsonTask.class, task -> {
            // We're going to read from this FileCollection, so we need to make sure that Gradle is aware of any
            // task dependencies necesary for fully populate the files (specifically, we need it to run 'jar').
            task.dependsOn(myView.getArtifacts().getArtifactFiles());

            task.getInputJsonFiles().set(myView.getArtifacts().getArtifactFiles());
        });
    }

    public abstract static class MergeDiagnosticsJsonTask extends DefaultTask {

        public MergeDiagnosticsJsonTask() {
            File out = new File(getProject().getBuildDir(), getName() + ".json");
            getOutputJsonFile().set(out);
        }

        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        public abstract Property<FileCollection> getInputJsonFiles();

        @OutputFile
        public abstract RegularFileProperty getOutputJsonFile();

        @TaskAction
        public void taskAction() {
            Diagnostics.SupportedDiagnostics aggregated =
                    Diagnostics.SupportedDiagnostics.of(getInputJsonFiles().get().getFiles().stream()
                            .flatMap(file -> Diagnostics.parse(getProject(), file).get().stream())
                            .distinct()
                            .sorted(Comparator.comparing(v -> v.type().toString()))
                            .collect(Collectors.toList()));

            File out = getOutputJsonFile().getAsFile().get();
            try {
                CreateManifestTask.jsonMapper.writeValue(out, aggregated);
            } catch (IOException e) {
                throw new GradleException("Failed to write " + out, e);
            }
        }
    }

    @CacheableTransform
    public abstract static class ExtractSingleFile implements TransformAction<ExtractSingleFile.Parameters> {
        private static final Logger log = LoggerFactory.getLogger(ExtractSingleFile.class);

        interface Parameters extends TransformParameters {
            @Input
            Property<String> getPathToExtract();
        }

        @PathSensitive(PathSensitivity.NAME_ONLY)
        @InputArtifact
        public abstract Provider<FileSystemLocation> getInputArtifact();

        @Override
        public void transform(TransformOutputs outputs) {
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
}
