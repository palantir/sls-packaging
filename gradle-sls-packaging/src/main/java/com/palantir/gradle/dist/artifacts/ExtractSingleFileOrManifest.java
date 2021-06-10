/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.dist.artifacts;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.jar.Manifest;
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
import org.gradle.util.GFileUtils;

@CacheableTransform
public abstract class ExtractSingleFileOrManifest implements TransformAction<FileAndManifestExtractParameter> {
    private static final String MANIFEST = "META-INF/MANIFEST.MF";

    @PathSensitive(PathSensitivity.NAME_ONLY)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public final void transform(TransformOutputs outputs) {
        File jarFile = getInputArtifact().get().getAsFile();
        String pathToExtract = getParameters().getPathToExtract().get();
        String manifestKey = getParameters().getKeyToExtract().get();

        try (ZipFile zipFile = new ZipFile(jarFile)) {
            ZipEntry fileEntry = zipFile.getEntry(pathToExtract);
            if (fileEntry != null) {
                try (InputStream is = zipFile.getInputStream(fileEntry)) {
                    String newFileName = com.google.common.io.Files.getNameWithoutExtension(jarFile.getName()) + "-"
                            + pathToExtract.replaceAll("/", "-");
                    File outputFile = outputs.file(newFileName);
                    Files.copy(is, outputFile.toPath());
                }
                return;
            }

            ZipEntry manifestEntry = zipFile.getEntry(MANIFEST);
            if (manifestEntry != null) {
                Manifest manifest = new Manifest(zipFile.getInputStream(manifestEntry));
                File outputFile = outputs.file("manifest.json");
                GFileUtils.writeFile(manifest.getMainAttributes().getValue(manifestKey), outputFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract '" + pathToExtract + "' from jar: " + jarFile, e);
        }
    }
}
