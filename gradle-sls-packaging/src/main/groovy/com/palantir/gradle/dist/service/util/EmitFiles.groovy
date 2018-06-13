/*
 * (c) Copyright 2015 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.gradle.dist.service.util

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.Map.Entry

class EmitFiles {

    static Path replaceVars(InputStream src, Path dest, Map<String, String> vars) {
        String text = new String(src.getText())

        for (Entry<String, String> entry : vars) {
            text = text.replaceAll(entry.key, entry.value)
        }

        // ensure output directory exists
        dest.getParent().toFile().mkdirs()

        // write content
        return Files.write(dest, text.getBytes(Charset.forName("UTF-8")))
    }

}
