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

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class DiagnosticsManifestPlugin implements Plugin<Project> {

    public static final Attribute<Boolean> DIAGNOSTIC_JSON_EXTRACTED =
            Attribute.of("diagnosticJsonExtracted", Boolean.class);

    // https://docs.gradle.org/current/userguide/artifact_transforms.html
    @CacheableTransform
    public abstract static class ExtractFileAction implements TransformAction<ExtractFileAction.Parameters> {
        private static final Logger log = LoggerFactory.getLogger(ExtractFileAction.class);

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

    @Override
    public void apply(Project project) {
        // project.getDependencies().getAttributesSchema().attribute(DIAGNOSTIC_JSON_EXTRACTED);
        project.getDependencies().getArtifactTypes().getByName("jar", it -> {
            it.getAttributes().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);
        });

        Configuration runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");
        runtimeClasspath.getAttributes().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);

        project.getDependencies().registerTransform(ExtractFileAction.class, details -> {
            details.getFrom().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);
            details.getFrom().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);
            details.getTo().attribute(DIAGNOSTIC_JSON_EXTRACTED, true);
            details.getParameters().getPathToExtract().set("sls-manifest/diagnostics.json");
        });

        // TODO(dfox): can we avoid needing this?
        Configuration consumable = project.getConfigurations().create("runtimeClasspath2", conf -> {
            conf.extendsFrom(project.getConfigurations().getByName("runtimeClasspath"));
            conf.getAttributes().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);
            conf.setDescription(
                    "Used by the DiagnosticsManifestPlugin, we just use this configuration to extract stuff");

            // empirically, seems like we need both of these
            conf.setCanBeConsumed(true);
            conf.setCanBeResolved(true);
        });
        // In order to get classes & resources from this project bundled into a jar, we take this 'self' dependency
        project.getDependencies().add(consumable.getName(), project);

        ArtifactView myView = consumable.getIncoming().artifactView(v -> {
            v.attributes(it -> {
                it.attribute(DIAGNOSTIC_JSON_EXTRACTED, true);
            });
        });

        project.getTasks().register("foo", DefaultTask.class, task -> {
            task.dependsOn(myView.getArtifacts().getArtifactFiles());
            task.doLast(t -> {
                System.out.println("DO THE TRANSFORM"
                        + myView.getArtifacts().getArtifactFiles().getFiles());
            });
        });
    }
}
