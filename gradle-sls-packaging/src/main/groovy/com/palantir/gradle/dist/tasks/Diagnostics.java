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

package com.palantir.gradle.dist.tasks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.plugins.JavaPluginConvention;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Diagnostics {
    private static final Logger log = LoggerFactory.getLogger(Diagnostics.class);
    static final String PATH_IN_JAR = "sls-debug/diagnostics.json";

    static List<DiagnosticType> loadFromConfiguration(Project current, Configuration configuration) {
        return configuration.getResolvedConfiguration().getResolvedArtifacts().stream()
                .map(artifact -> {
                    ComponentIdentifier id = artifact.getId().getComponentIdentifier();
                    if (id instanceof ProjectComponentIdentifier) {
                        Project dependencyProject = current.project(((ProjectComponentIdentifier) id).getProjectPath());
                        return maybeGetSourceFileFromLocalProject(dependencyProject);
                    } else {
                        return maybeExtractFromJar(artifact.getFile(), id);
                    }
                })
                .filter(Optional::isPresent)
                .flatMap(isPresent -> isPresent.get().types().stream())
                .distinct() // would be kinda weird if multiple jars claim to provide the same diagnostic type??
                .sorted(Comparator.comparing(DiagnosticType::toString))
                .collect(Collectors.toList());
    }

    static List<SupportedDiagnostic> asManifestExtension(List<DiagnosticType> items) {
        return items.stream().map(ImmutableSupportedDiagnostic::of).collect(Collectors.toList());
    }

    @Value.Immutable
    @JsonDeserialize(as = ImmutableEmbeddedInJar.class)
    interface EmbeddedInJar {
        List<DiagnosticType> types();
    }

    @Value.Immutable
    interface SupportedDiagnostic {
        @Value.Parameter
        DiagnosticType type();
    }

    private static Optional<EmbeddedInJar> maybeGetSourceFileFromLocalProject(Project proj) {
        JavaPluginConvention javaPlugin = proj.getConvention().findPlugin(JavaPluginConvention.class);
        if (javaPlugin == null) {
            return Optional.empty();
        }

        Set<File> sourceFiles = javaPlugin
                .getSourceSets()
                .getByName("main")
                .getResources()
                .getAsFileTree()
                .filter(file -> {
                    return file.toString().endsWith(PATH_IN_JAR);
                })
                .getFiles();
        if (sourceFiles.isEmpty()) {
            return Optional.empty();
        }
        if (sourceFiles.size() > 1) {
            throw new GradleException("Expecting to find 0 or 1 files, found: " + sourceFiles);
        }
        File file = Iterables.getOnlyElement(sourceFiles);
        try {
            return Optional.of(CreateManifestTask.jsonMapper.readValue(file, EmbeddedInJar.class));
        } catch (IOException e) {
            throw new GradleException(
                    "Failed to deserialize '" + file + "', expecting something like "
                            + "{\"types\":[\"foo.v1\", \"bar.v1\"]} ",
                    e);
        }
    }

    private static Optional<EmbeddedInJar> maybeExtractFromJar(File jarFile, ComponentIdentifier idForLogging) {
        if (!jarFile.exists()) {
            log.debug("Artifact did not exist: {}", jarFile);
            return Optional.empty();
        } else if (!Files.getFileExtension(jarFile.getName()).equals("jar")) {
            log.debug("Artifact is not a jar: {}", jarFile);
            return Optional.empty();
        }

        try (ZipFile zipFile = new ZipFile(jarFile)) {
            ZipEntry zipEntry = zipFile.getEntry(PATH_IN_JAR);
            if (zipEntry == null) {
                log.debug("Unable to find '{}' in JAR: {}", PATH_IN_JAR, idForLogging);
                return Optional.empty();
            }

            try (InputStream is = zipFile.getInputStream(zipEntry)) {
                return Optional.of(CreateManifestTask.jsonMapper.readValue(is, EmbeddedInJar.class));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load from jar: " + idForLogging);
        }
    }

    /**
     * A {@link DiagnosticType} is an identifier that uniquely identifies the operation and output format for the
     * diagnostics. Type names must be specific, reasonably expected to be unique, and versioned to allow for future
     * major changes of the payload structure. DiagnosticTypes must match the regular expression
     * {@code ([a-z0-9]+\.)+v[0-9]+}, i.e. be lower-case, dot-delimited, and end with a version suffix. For example, the
     * {@code threaddump.v1} diagnosticType  might indicate a value of ThreadDumpV1 from the DiagnosticLogV1 definition.
     */
    public static final class DiagnosticType {
        private static final Pattern TYPE_PATTERN = Pattern.compile("([a-z0-9]+\\.)+v[0-9]+");

        private final String diagnosticTypeString;

        @JsonCreator
        public static DiagnosticType of(String diagnosticTypeString) {
            Preconditions.checkNotNull(diagnosticTypeString, "Diagnostic type string is required");
            if (!TYPE_PATTERN.matcher(diagnosticTypeString).matches()) {
                throw new SafeIllegalArgumentException(
                        "Diagnostic types must match pattern",
                        SafeArg.of("diagnosticType", diagnosticTypeString),
                        SafeArg.of("pattern", TYPE_PATTERN.pattern()));
            }
            return new DiagnosticType(diagnosticTypeString);
        }

        private DiagnosticType(String diagnosticTypeString) {
            this.diagnosticTypeString = diagnosticTypeString;
        }

        @Override
        @JsonValue
        public String toString() {
            return diagnosticTypeString;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            DiagnosticType that = (DiagnosticType) other;
            return Objects.equals(diagnosticTypeString, that.diagnosticTypeString);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(diagnosticTypeString);
        }
    }

    private Diagnostics() {}
}
