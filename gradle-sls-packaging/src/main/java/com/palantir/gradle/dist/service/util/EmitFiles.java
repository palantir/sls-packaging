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
package com.palantir.gradle.dist.service.util;

import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class EmitFiles {
    public static Path replaceVars(InputStream src, Path dest, Map<String, String> vars) throws IOException {
        String text;
        try (Reader reader = new InputStreamReader(src, StandardCharsets.UTF_8)) {
            text = CharStreams.toString(reader);
        }

        for (Map.Entry<String, String> entry : vars.entrySet()) {
            text = text.replaceAll(entry.getKey(), entry.getValue());
        }

        // ensure output directory exists
        dest.getParent().toFile().mkdirs();

        // write content
        return Files.write(dest, text.getBytes(StandardCharsets.UTF_8));
    }

    private EmitFiles() {}
}
