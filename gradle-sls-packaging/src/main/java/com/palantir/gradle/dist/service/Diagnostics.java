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
import com.palantir.gradle.dist.ObjectMappers;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.gradle.api.GradleException;

public final class Diagnostics {
    private static final String EXAMPLE =
            "[{\"type\":\"foo.v1\", \"docs\":\"...\"}, \"{\"type\":\"bar.v1\", " + "\"docs\":\"...\"}]";

    public static List<ObjectNode> parse(File file) {
        String string = null;
        try {
            string = Files.readString(file.toPath()).trim();
            return ObjectMappers.jsonMapper.readValue(string, new TypeReference<>() {});
        } catch (IOException e) {
            throw new GradleException(
                    String.format(
                            "Failed to deserialize '%s', expecting something like '%s' but was '%s'",
                            file.getAbsolutePath(), EXAMPLE, string),
                    e);
        }
    }

    private Diagnostics() {}
}
