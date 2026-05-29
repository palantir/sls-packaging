/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service.tasks;

import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

@AutoParallelizable
public final class ExplodeClasspath {
    interface Params {
        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        ConfigurableFileCollection getClasspath();

        @OutputDirectory
        DirectoryProperty getOutputDirectory();
    }

    static void action(Params params) {
        Path outDir = params.getOutputDirectory().get().getAsFile().toPath();
        List<Path> jars = params.getClasspath().getFiles().stream()
                .map(f -> f.toPath())
                .toList();

        try {
            explodeClasspath(jars, outDir);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static void explodeClasspath(List<Path> jars, Path outDir) throws IOException {
        Map<String, List<String>> serviceLines = new LinkedHashMap<>();

        for (Path jar : jars) {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(jar))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName();
                    Path target = outDir.resolve(name);

                    if (name.startsWith("META-INF/services/")) {
                        String svcName = name.substring("META-INF/services/".length());
                        serviceLines
                                .computeIfAbsent(svcName, _k -> new ArrayList<>())
                                .addAll(new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8))
                                        .lines()
                                        .toList());
                        continue;
                    }

                    if (!Files.exists(target)) {
                        Files.createDirectories(target.getParent());
                        Files.copy(zis, target);
                    }
                }
            }
        }

        for (Map.Entry<String, List<String>> e : serviceLines.entrySet()) {
            Path svcFile = outDir.resolve("META-INF/services").resolve(e.getKey());
            Files.createDirectories(svcFile.getParent());
            List<String> merged = e.getValue().stream()
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .distinct()
                    .toList();
            Files.write(svcFile, merged);
        }
    }

    private ExplodeClasspath() {}
}
