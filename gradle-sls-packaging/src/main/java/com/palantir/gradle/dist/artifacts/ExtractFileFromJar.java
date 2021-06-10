/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.dist.artifacts;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CacheableTransform
public abstract class ExtractFileFromJar implements TransformAction<FileExtractParameter> {
    private static final Logger log = LoggerFactory.getLogger(ExtractFileFromJar.class);

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
