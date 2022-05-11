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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.immutables.value.Value;

/**
 * This utility class reuses the {@code Add-Exports} and {@code Add-Opens} manifest entries
 * defined in <a href="https://openjdk.java.net/jeps/261">JEP-261</a> to collect required
 * exports and opens from the runtime classpath so they may be applied to the static configuration.
 *
 * Note that this mirrors {@code Add-Exports} plumbing from the {@code BaselineModuleJvmArgs}
 * plugin <a href="https://github.com/palantir/gradle-baseline/pull/1944">gradle-baseline#1944</a>.
 */
final class ModuleArgs {

    private static final String ADD_EXPORTS_ATTRIBUTE = "Add-Exports";
    private static final String ADD_OPENS_ATTRIBUTE = "Add-Opens";

    private static final Splitter ENTRY_SPLITTER =
            Splitter.on(' ').trimResults().omitEmptyStrings();

    // Exists for backcompat until infrastructure has rolled out with Add-Exports manifest values.
    // Support safepoint metrics from the internal sun.management package in production. We prefer not
    // to use '--illegal-access=permit' so that we can avoid unintentional and unbounded illegal access
    // that we aren't aware of.
    private static final ImmutableList<String> DEFAULT_EXPORTS = ImmutableList.of("java.management/sun.management");

    static ImmutableList<String> collectClasspathArgs(
            Project project, JavaVersion javaVersion, FileCollection classpath) {
        // --add-exports is unnecessary prior to java 16
        if (javaVersion.compareTo(JavaVersion.toVersion("16")) < 0) {
            return ImmutableList.of();
        }

        Set<File> classpathFiles = loadAllClasspathFiles(project, classpath);

        ImmutableList<JarManifestModuleInfo> classpathInfo = classpathFiles.stream()
                .map(file -> {
                    try {
                        if (file.getName().endsWith(".jar") && file.isFile()) {
                            try (JarFile jar = new JarFile(file)) {
                                java.util.jar.Manifest maybeJarManifest = jar.getManifest();
                                Optional<JarManifestModuleInfo> parsedModuleInfo = parseModuleInfo(maybeJarManifest);
                                project.getLogger()
                                        .debug("Jar '{}' produced manifest info: {}", file, parsedModuleInfo);
                                return parsedModuleInfo.orElse(null);
                            }
                        }
                        return null;
                    } catch (IOException e) {
                        project.getLogger().warn("Failed to check jar {} for manifest attributes", file, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(ImmutableList.toImmutableList());
        Stream<String> exports = Stream.concat(
                        DEFAULT_EXPORTS.stream(), classpathInfo.stream().flatMap(info -> info.exports().stream()))
                .distinct()
                .sorted()
                .flatMap(ModuleArgs::addExportArg);
        Stream<String> opens = classpathInfo.stream()
                .flatMap(info -> info.opens().stream())
                .distinct()
                .sorted()
                .flatMap(ModuleArgs::addOpensArg);
        return Stream.concat(exports, opens).collect(ImmutableList.toImmutableList());
    }

    private static Set<File> loadAllClasspathFiles(Project project, FileCollection classpath) {
        return classpath.getFiles().stream()
                .filter(f -> f.isFile() && f.getName().endsWith(".jar"))
                .flatMap(f -> {
                    try (JarFile jar = new JarFile(f)) {
                        java.util.jar.Manifest jarManifest = jar.getManifest();
                        if (jarManifest == null) {
                            return Stream.empty();
                        }

                        List<String> classpathEntries =
                                readManifestAttribute(jarManifest, Attributes.Name.CLASS_PATH.toString());

                        if (classpathEntries.isEmpty()) {
                            return Stream.of(f);
                        }

                        return Stream.concat(Stream.of(f), classpathEntries.stream().map(e -> new File(f.getParentFile(), e)));
                    } catch (IOException e) {
                        project.getLogger().warn("Failed to load manifest entry for jar {}", f, e);
                        return Stream.empty();
                    }
                }).collect(Collectors.toSet());
    }

    private static Optional<JarManifestModuleInfo> parseModuleInfo(@Nullable java.util.jar.Manifest jarManifest) {
        return Optional.ofNullable(jarManifest)
                .<JarManifestModuleInfo>map(manifest -> JarManifestModuleInfo.builder()
                        .exports(readManifestAttribute(manifest, ADD_EXPORTS_ATTRIBUTE))
                        .opens(readManifestAttribute(manifest, ADD_OPENS_ATTRIBUTE))
                        .build())
                .filter(JarManifestModuleInfo::isPresent);
    }

    private static List<String> readManifestAttribute(java.util.jar.Manifest jarManifest, String attribute) {
        return Optional.ofNullable(
                        Strings.emptyToNull(jarManifest.getMainAttributes().getValue(attribute)))
                .map(ENTRY_SPLITTER::splitToList)
                .orElseGet(ImmutableList::of);
    }

    private static Stream<String> addExportArg(String modulePackagePair) {
        return Stream.of("--add-exports", modulePackagePair + "=ALL-UNNAMED");
    }

    private static Stream<String> addOpensArg(String modulePackagePair) {
        return Stream.of("--add-opens", modulePackagePair + "=ALL-UNNAMED");
    }

    private ModuleArgs() {}

    @Value.Immutable
    interface JarManifestModuleInfo {
        ImmutableList<String> exports();

        ImmutableList<String> opens();

        default boolean isEmpty() {
            return exports().isEmpty() && opens().isEmpty();
        }

        default boolean isPresent() {
            return !isEmpty();
        }

        static Builder builder() {
            return new Builder();
        }

        class Builder extends ImmutableJarManifestModuleInfo.Builder {}
    }
}
