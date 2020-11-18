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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public final class DiagnosticsManifestPlugin implements Plugin<Project> {
    public static final Attribute<Boolean> DIAGNOSTIC_JSON_EXTRACTED =
            Attribute.of("diagnosticJsonExtracted1", Boolean.class);

    @CacheableTransform
    public abstract static class ExtractFileAction implements TransformAction<TransformParameters.None> {
        @PathSensitive(PathSensitivity.NAME_ONLY)
        @InputArtifact
        public abstract Provider<FileSystemLocation> getInputArtifact();

        @Override
        public void transform(TransformOutputs outputs) {
            File inputJar = getInputArtifact().get().getAsFile();

            if (inputJar.toString().contains("jackson-core")) {

                File outFile = outputs.file(inputJar.getName() + ".json1");
                final String contents = "HELLO " + inputJar.length();
                try {
                    Files.write(outFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException("Failed", e);
                }
            }
            // outputs.file(getInputArtifact());
        }
    }

    @Override
    public void apply(Project project) {
        project.getDependencies().getAttributesSchema().attribute(DIAGNOSTIC_JSON_EXTRACTED);
        project.getDependencies().getArtifactTypes().getByName("jar", it -> {
            it.getAttributes().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);
        });

        Configuration runtimeClasspath = project.getConfigurations().getByName("runtimeClasspath");
        runtimeClasspath.getAttributes().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);

        project.getDependencies().registerTransform(ExtractFileAction.class, details -> {
            details.getFrom().attribute(DIAGNOSTIC_JSON_EXTRACTED, false);
            details.getTo().attribute(DIAGNOSTIC_JSON_EXTRACTED, true);
        });

        // project.getConfigurations().create("runtimeClasspathExtracted", )

        project.getTasks().register("foo", DefaultTask.class, foo -> {
            foo.doLast(t -> {
                final ArtifactCollection ac = runtimeClasspath
                        .getIncoming()
                        .artifactView(view -> {
                            view.attributes(it -> {
                                it.attribute(DIAGNOSTIC_JSON_EXTRACTED, true);
                            });
                            view.lenient(false);
                        })
                        .getArtifacts();
                System.out.println("DO THE TRANSFORM" + ac.getArtifactFiles().getFiles());
            });
        });

        //
        // project.afterEvaluate(p -> {
        //     System.out.println("POOP" + av.getArtifacts().getArtifactFiles().getFiles());
        // });
    }
}
