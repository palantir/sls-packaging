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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
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
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DiagnosticsManifestPlugin implements Plugin<Project> {

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
    public void apply(Project project) {
        // The 'artifactType' attribute is defined by
        // org.gradle.api.internal.artifacts.ArtifactAttributes.ARTIFACT_FORMAT.
        // Annoyingly that class is 'internal', even though the values defined in 'ArtifactTypeDefinition' are public!
        Attribute<String> artifactType = Attribute.of("artifactType", String.class);

        // (1) this 'jar -> extracted file' mapping is crucial to analyze downloaded jars (e.g. witchcraft)
        project.getDependencies().registerTransform(ExtractFileFromJar.class, details -> {
            details.getParameters().getPathToExtract().set(FILE_TO_EXTRACT);

            details.getFrom().attribute(artifactType, ArtifactTypeDefinition.JAR_TYPE);
            details.getTo().attribute(artifactType, MY_ATTRIBUTE);

            // It's not _strictly_ necessary to define the mapping for the 'org.gradle.libraryelements' attribute,
            // but it looks a bit weird for something to still be marked as a 'jar' when it's clearly not.
            details.getFrom()
                    .attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            details.getTo()
                    .attribute(
                            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                            project.getObjects().named(LibraryElements.class, MY_ATTRIBUTE));
        });

        // (2) this 'java-resources -> extracted file' thing is just a 'shortcut' so that gradle can complete this
        // task without needing to compile the java source files from any local projects. If we didn't define this,
        // then gradle would turn src dirs into a compiled jar, then run that through the transform (1) above.
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

        // Ideally I'd set our desired attributes right on this configuration, but it seems Gradle doesn't know how to
        // use our transforms to bridge the gap when we have cross project dependencies :/
        Configuration consumable = project.getConfigurations().create("runtimeClasspathForDiagnostics", conf -> {
            conf.extendsFrom(project.getConfigurations().getByName("runtimeClasspath"));
            conf.setDescription("DiagnosticsManifestPlugin uses this configuration to extract single file");
            conf.setCanBeConsumed(true);
            conf.setCanBeResolved(true);
            conf.setVisible(false);
        });
        project.getDependencies().add(consumable.getName(), project);
        ArtifactView myView = consumable.getIncoming().artifactView(v -> {
            v.attributes(it -> {
                // this is where we 'declare' the destination attributes we care about, and trust gradle to apply
                // the right transforms
                it.attribute(artifactType, MY_ATTRIBUTE);
                it.attribute(
                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        project.getObjects().named(LibraryElements.class, MY_ATTRIBUTE));
            });
        });

        project.getTasks().register(mergeDiagnosticsJson, MergeDiagnosticsJsonTask.class, task -> {
            // We're going to read from this FileCollection, so we need to make sure that Gradle is aware of any
            // task dependencies necessary for fully populate the files (e.g. maybe generating some resources???)
            task.dependsOn(myView.getArtifacts().getArtifactFiles());

            task.getInputJsonFiles().set(myView.getArtifacts().getArtifactFiles());

            File out = new File(project.getBuildDir(), task.getName() + ".json");
            task.getOutputJsonFile().set(out);
        });
    }

    @CacheableTask
    public abstract static class MergeDiagnosticsJsonTask extends DefaultTask {

        @InputFiles
        @PathSensitive(PathSensitivity.NONE)
        public abstract Property<FileCollection> getInputJsonFiles();

        @OutputFile
        public abstract RegularFileProperty getOutputJsonFile();

        @TaskAction
        public final void taskAction() {
            List<Diagnostics.SupportedDiagnostic> aggregated = getInputJsonFiles().get().getFiles().stream()
                    .flatMap(file -> Diagnostics.parse(getProject(), file).stream())
                    .distinct()
                    .sorted(Comparator.comparing(v -> v.type().toString()))
                    .collect(Collectors.toList());

            File out = getOutputJsonFile().getAsFile().get();
            try {
                CreateManifestTask.jsonMapper.writeValue(out, aggregated);
            } catch (IOException e) {
                throw new GradleException("Failed to write " + out, e);
            }
        }

        public final Provider<List<Diagnostics.SupportedDiagnostic>> asProvider() {
            return getOutputJsonFile().getAsFile().map(file -> Diagnostics.parse(getProject(), file));
        }
    }

    @CacheableTransform
    public abstract static class ExtractFileFromJar implements TransformAction<ExtractFileFromJar.Parameters> {
        private static final Logger log = LoggerFactory.getLogger(ExtractFileFromJar.class);

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
        private static final Logger log = LoggerFactory.getLogger(SelectSingleFile.class);

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
