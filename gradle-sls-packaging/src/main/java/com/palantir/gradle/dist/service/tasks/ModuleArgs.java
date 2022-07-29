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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
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

        Map<File, JarManifestModuleInfo> parsedJarManifests = classpath.getFiles().stream()
                .<Entry<File, JarManifestModuleInfo>>map(file -> {
                    try {
                        if (file.getName().endsWith(".jar") && file.isFile()) {
                            Optional<JarManifestModuleInfo> parsedModuleInfo = JarManifestModuleInfo.fromJar(file);
                            project.getLogger().debug("Jar '{}' produced manifest info: {}", file, parsedModuleInfo);
                            return Maps.immutableEntry(file, parsedModuleInfo.orElse(null));
                        } else {
                            project.getLogger().info("File {} wasn't a JAR or file", file);
                        }
                    } catch (IOException e) {
                        project.getLogger().warn("Failed to check jar {} for manifest attributes", file, e);
                    }
                    return Maps.immutableEntry(file, null);
                })
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(
                        Entry::getKey,
                        Entry::getValue,
                        (_left, _right) -> {
                            throw new UnsupportedOperationException();
                        },
                        LinkedHashMap::new));

        Collection<JarManifestModuleInfo> classpathInfo = parsedJarManifests.values();
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
        Stream<String> enablePreview = enablePreview(javaVersion, parsedJarManifests);

        return Stream.of(exports, opens, enablePreview)
                .flatMap(Function.identity())
                .collect(ImmutableList.toImmutableList());
    }

    private static Stream<String> enablePreview(
            JavaVersion javaVersion, Map<File, JarManifestModuleInfo> parsedJarManifests) {
        Map<String, Collection<String>> enablePreviewFromJar = parsedJarManifests.entrySet().stream()
                .filter(entry -> entry.getValue().enablePreview().isPresent())
                .collect(Multimaps.toMultimap(
                        entry -> entry.getValue().enablePreview().get(),
                        entry -> entry.getKey().getName(),
                        () -> MultimapBuilder.hashKeys().arrayListValues().build()))
                .asMap();

        if (enablePreviewFromJar.size() > 1) {
            throw new RuntimeException("Unable to add '--enable-preview' because classpath jars have embedded "
                    + JarManifestModuleInfo.ENABLE_PREVIEW_ATTRIBUTE + " attribute with different versions:\n"
                    + enablePreviewFromJar);
        }

        if (enablePreviewFromJar.size() == 1) {
            String enablePreviewVersion = Iterables.getOnlyElement(enablePreviewFromJar.keySet());
            Preconditions.checkState(
                    enablePreviewVersion.equals(javaVersion.toString()),
                    "Runtime java version (" + javaVersion + ") must match version from embedded "
                            + JarManifestModuleInfo.ENABLE_PREVIEW_ATTRIBUTE + " attribute (" + enablePreviewVersion
                            + ") from:\n" + Iterables.getOnlyElement(enablePreviewFromJar.values()));
            return Stream.of("--enable-preview");
        }

        return Stream.empty();
    }

    private static Stream<String> addExportArg(String modulePackagePair) {
        return Stream.of("--add-exports", modulePackagePair + "=ALL-UNNAMED");
    }

    private static Stream<String> addOpensArg(String modulePackagePair) {
        return Stream.of("--add-opens", modulePackagePair + "=ALL-UNNAMED");
    }

    private ModuleArgs() {}

    /** Values extracted from a jar's manifest - see {@link #fromJar}. */
    @Value.Immutable
    interface JarManifestModuleInfo {
        Splitter ENTRY_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();
        String ADD_EXPORTS_ATTRIBUTE = "Add-Exports";
        String ADD_OPENS_ATTRIBUTE = "Add-Opens";
        String ENABLE_PREVIEW_ATTRIBUTE = "Baseline-Enable-Preview";

        ImmutableList<String> exports();

        ImmutableList<String> opens();

        /**
         * Signifies that {@code --enable-preview} should be added at runtime AND the specific java runtime version
         * that must be used. (Code compiled with --enable-preview must run on _exactly_ the same java version).
         * */
        Optional<String> enablePreview();

        default boolean isEmpty() {
            return exports().isEmpty() && opens().isEmpty() && enablePreview().isEmpty();
        }

        default boolean isPresent() {
            return !isEmpty();
        }

        static Optional<JarManifestModuleInfo> fromJar(File file) throws IOException {
            try (JarFile jar = new JarFile(file)) {
                java.util.jar.Manifest maybeJarManifest = jar.getManifest();
                return JarManifestModuleInfo.fromJarManifest(maybeJarManifest);
            }
        }

        private static Optional<JarManifestModuleInfo> fromJarManifest(@Nullable java.util.jar.Manifest jarManifest) {
            return Optional.ofNullable(jarManifest)
                    .<JarManifestModuleInfo>map(manifest -> builder()
                            .exports(readListAttribute(manifest, ADD_EXPORTS_ATTRIBUTE))
                            .opens(readListAttribute(manifest, ADD_OPENS_ATTRIBUTE))
                            .enablePreview(readOptionalAttribute(manifest, ENABLE_PREVIEW_ATTRIBUTE))
                            .build())
                    .filter(JarManifestModuleInfo::isPresent);
        }

        private static List<String> readListAttribute(java.util.jar.Manifest jarManifest, String attribute) {
            return readOptionalAttribute(jarManifest, attribute)
                    .map(ENTRY_SPLITTER::splitToList)
                    .orElseGet(ImmutableList::of);
        }

        private static Optional<String> readOptionalAttribute(java.util.jar.Manifest jarManifest, String attribute) {
            return Optional.ofNullable(
                    Strings.emptyToNull(jarManifest.getMainAttributes().getValue(attribute)));
        }

        static Builder builder() {
            return new Builder();
        }

        class Builder extends ImmutableJarManifestModuleInfo.Builder {}
    }
}
