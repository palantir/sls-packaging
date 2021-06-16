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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.palantir.gradle.dist.Serializations;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Diagnostics {
    private static final Logger log = LoggerFactory.getLogger(Diagnostics.class);
    private static final String EXAMPLE =
            "[{\"type\":\"foo.v1\", \"docs\":\"...\"}, \"{\"type\":\"bar.v1\", " + "\"docs\":\"...\"}]";

    public static List<ObjectNode> parse(Project proj, File file) {
        Path relativePath = proj.getRootDir().toPath().relativize(file.toPath());
        String string = null;
        try {
            string = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
            List<ObjectNode> value =
                    Serializations.jsonMapper.readValue(string, new TypeReference<List<ObjectNode>>() {});
            log.info("Deserialized '{}': '{}'", relativePath, value);
            return value;
        } catch (IOException e) {
            throw new GradleException(
                    String.format(
                            "Failed to deserialize '%s', expecting something like '%s' but was '%s'",
                            relativePath, EXAMPLE, string),
                    e);
        }
    }

    private Diagnostics() {}
}
