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

package com.palantir.gradle.dist.service.tasks;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

/**
 * This utility class reuses the {@code Add-Exports} manifest entry defined in
 * <a href="https://openjdk.java.net/jeps/261">JEP-261</a> to collect required exports
 * from the runtime classpath so they may be applied to the static configuration.
 *
 * Note that this mirrors {@code Add-Exports} plumbing from
 * <a href="https://github.com/palantir/gradle-baseline/pull/1944">gradle-baseline#1944</a>.
 */
final class ModuleExports {

    private static final String ADD_EXPORTS_ATTRIBUTE = "Add-Exports";
    private static final Splitter EXPORT_SPLITTER =
            Splitter.on(' ').trimResults().omitEmptyStrings();

    // Exists for backcompat until infrastructure has rolled out with Add-Exports manifest values.
    // Support safepoint metrics from the internal sun.management package in production. We prefer not
    // to use '--illegal-access=permit' so that we can avoid unintentional and unbounded illegal access
    // that we aren't aware of.
    private static final ImmutableList<String> DEFAULT_EXPORTS = ImmutableList.of("java.management/sun.management");

    static ImmutableList<String> getExports(Project project, JavaVersion javaVersion, FileCollection classpath) {
        // --add-exports is unnecessary prior to java 16
        if (javaVersion.compareTo(JavaVersion.toVersion("16")) < 0) {
            return ImmutableList.of();
        }

        return Stream.concat(
                        classpath.getFiles().stream().flatMap(file -> {
                            try {
                                if (file.getName().endsWith(".jar") && file.isFile()) {
                                    try (JarFile jar = new JarFile(file)) {
                                        java.util.jar.Manifest jarManifest = jar.getManifest();
                                        if (jarManifest == null) {
                                            project.getLogger().debug("Jar '{}' has no manifest", file);
                                            return Stream.empty();
                                        }
                                        String value =
                                                jarManifest.getMainAttributes().getValue(ADD_EXPORTS_ATTRIBUTE);
                                        if (Strings.isNullOrEmpty(value)) {
                                            return Stream.empty();
                                        }
                                        project.getLogger()
                                                .debug(
                                                        "Found manifest entry {}: {} in jar {}",
                                                        ADD_EXPORTS_ATTRIBUTE,
                                                        value,
                                                        file);
                                        return EXPORT_SPLITTER.splitToStream(value);
                                    }
                                }
                                return Stream.empty();
                            } catch (IOException e) {
                                project.getLogger().warn("Failed to check jar {} for manifest attributes", file, e);
                                return Stream.empty();
                            }
                        }),
                        DEFAULT_EXPORTS.stream())
                .distinct()
                .sorted()
                .flatMap(modulePackagePair -> Stream.of("--add-exports", modulePackagePair + "=ALL-UNNAMED"))
                .collect(ImmutableList.toImmutableList());
    }

    private ModuleExports() {}
}
