/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.dist.ObjectMappers;
import com.palantir.gradle.dist.RecommendedProductDependencies;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @CacheableTransform
public abstract class ExtractSingleFileOrManifest implements TransformAction<FileAndManifestExtractParameter> {
    private static final Logger log = LoggerFactory.getLogger(ExtractSingleFileOrManifest.class);

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public final void transform(TransformOutputs outputs) {
        File jarFile = getInputArtifact().get().getAsFile();
        String pathToExtract = getParameters().getPathToExtract().get();
        String key = getParameters().getKeyToExtract().get();

        try (JarFile jar = new JarFile(jarFile)) {
            ZipEntry fileEntry = jar.getEntry(pathToExtract);
            if (fileEntry != null) {
                try (InputStream is = jar.getInputStream(fileEntry)) {
                    String newFileName = com.google.common.io.Files.getNameWithoutExtension(jarFile.getName()) + "-"
                            + pathToExtract.replaceAll("/", "-");
                    File outputFile = outputs.file(newFileName);

                    String value = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    Files.write(outputFile.toPath(), value.getBytes(StandardCharsets.UTF_8));
                    logPdeps(jarFile.toString(), value);
                }
                return;
            }

            Optional<String> value = Optional.ofNullable(jar.getManifest())
                    .map(manifest -> manifest.getMainAttributes().getValue(key));
            if (value.isPresent()) {
                File outputFile = outputs.file("manifest.json");
                Files.write(outputFile.toPath(), value.get().getBytes(StandardCharsets.UTF_8));
                logPdeps(jarFile.toString(), value.get());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract '" + pathToExtract + "' from jar: " + jarFile, e);
        }
    }

    private void logPdeps(String source, String value) {
        try {
            RecommendedProductDependencies recommendedProductDependencies =
                    ObjectMappers.jsonMapper.readValue(value, RecommendedProductDependencies.class);
            logPdeps(source, recommendedProductDependencies);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read pdeps file", e);
        }
    }

    private void logPdeps(String source, RecommendedProductDependencies productDependencies) {
        List<String> pdeps = productDependencies.recommendedProductDependencies().stream()
                .map(pdep -> String.format(
                        "%s:%s:%s", pdep.getProductGroup(), pdep.getProductName(), pdep.getMinimumVersion()))
                .collect(Collectors.toList());
        log.info(">>> Extracted pdeps from jar {}: {}", source, pdeps);
    }
}
