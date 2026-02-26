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

package com.palantir.gradle.dist.service.utils;

import com.palantir.gradle.testing.project.GradleProject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class TestUtils {

    public static Optional<File> findJarInLibDirectory(
            GradleProject project, String serviceVersion, String namePattern) {
        List<File> libFiles = Optional.ofNullable(project.file("dist/service-name-" + serviceVersion + "/service/lib/")
                        .path()
                        .toFile()
                        .listFiles())
                .map(Arrays::asList)
                .orElseGet(List::of);
        return libFiles.stream()
                .filter(file -> file.getName().matches(namePattern))
                .findAny();
    }

    public static boolean hasJarInLibDirectory(GradleProject project, String serviceVersion, String namePattern) {
        return findJarInLibDirectory(project, serviceVersion, namePattern).isPresent();
    }

    public static List<String> extractClasspathEntriesFromScript(
            GradleProject project, String serviceVersion, String scriptName) {
        String startScriptContent = project.file("dist/service-name-" + serviceVersion + "/service/bin/" + scriptName)
                .text();

        Pattern pattern = Pattern.compile("CLASSPATH=(.*)");
        Matcher matcher = pattern.matcher(startScriptContent);
        if (matcher.find()) {
            String classpath = matcher.group(1);
            return Arrays.asList(classpath.split(":"));
        }
        throw new RuntimeException("Could not find CLASSPATH in start script");
    }

    public static String readFromZip(File zipFile, String pathInZipFile) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().equals(pathInZipFile)) {
                    return new String(zf.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalArgumentException("Entry not found: " + pathInZipFile);
    }

    private TestUtils() {}
}
