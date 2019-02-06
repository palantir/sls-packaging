/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.util.GFileUtils;

public final class ProductDependencyLockFile {

    private static final String HEADER = "# Run ./gradlew --write-locks to regenerate this file\n";
    private static final Pattern REGEX =
            Pattern.compile("^(?<group>[^:]+):(?<name>[^: ]+) \\((?<min>[^,]+), (?<max>[^)]+)\\)$");

    public static List<ProductDependency> fromFile(File file) {
        try {
            return Files.lines(file.toPath())
                    .filter(line -> !isComment(line))
                    .map(ProductDependencyLockFile::parseLine)
                    .collect(toList());
        } catch (IOException e) {
            throw new RuntimeException("TODO");
        }
    }

    public static void writeToFile(File file, List<ProductDependency> deps) {
        String contents = deps.stream()
                .map(dep -> String.format("%s:%s (%s, %s)",
                        dep.getProductGroup(),
                        dep.getProductName(),
                        dep.getMinimumVersion(),
                        dep.getMaximumVersion()))
                .collect(Collectors.joining("\n", HEADER, ""));

        GFileUtils.writeFile(contents, file);
    }

    private static ProductDependency parseLine(String line) {
        Matcher matcher = REGEX.matcher(line);
        Preconditions.checkState(matcher.matches(), "line must match: %s", line);
        return new ProductDependency(
                matcher.group("group"),
                matcher.group("name"),
                matcher.group("min"),
                matcher.group("max"),
                null);
    }

    private static boolean isComment(String line) {
        return line.startsWith("#") || line.trim().isEmpty();
    }
}
