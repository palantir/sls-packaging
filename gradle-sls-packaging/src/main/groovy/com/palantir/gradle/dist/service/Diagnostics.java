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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.palantir.gradle.dist.tasks.CreateManifestTask;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Diagnostics {
    private static final Logger log = LoggerFactory.getLogger(Diagnostics.class);

    @Value.Immutable
    public abstract static class SupportedDiagnostics {
        @Value.Parameter
        @JsonValue
        public abstract List<SupportedDiagnostic> get();

        @JsonCreator
        public static SupportedDiagnostics of(List<SupportedDiagnostic> items) {
            return ImmutableSupportedDiagnostics.of(items);
        }

        @Override
        public final String toString() {
            return get().stream().map(entry -> entry.type().toString()).collect(Collectors.joining(", ", "[", "]"));
        }
    }

    // This is the format the sls-spec wants list items to be. <code>{type: "foo.v1"}</code>.
    @Value.Immutable
    @JsonDeserialize(as = ImmutableSupportedDiagnostic.class)
    public interface SupportedDiagnostic {
        @Value.Parameter
        DiagnosticType type();
    }

    public static SupportedDiagnostics parse(Project proj, File file) {
        Path relativePath = proj.getRootDir().toPath().relativize(file.toPath());
        String string = null;
        try {
            string = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
            SupportedDiagnostics value = CreateManifestTask.jsonMapper.readValue(string, SupportedDiagnostics.class);
            log.info("Found diagnostics in local project '{}': '{}'", relativePath, value);
            return value;
        } catch (IOException e) {
            throw new GradleException(
                    String.format(
                            "Failed to deserialize '%s', "
                                    + "expecting something like '[{\"type\":\"foo.v1\"}, {\"type\":\"bar.v1\"}]' "
                                    + "but was '%s'",
                            relativePath, string),
                    e);
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
