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

package com.palantir.gradle.dist.service.util;

import com.google.common.io.Files;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.util.GFileUtils;

public final class MainClassResolver {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package ([^\n;]+)");

    public static String resolveMainClass(Project project) {
        SourceSet main = project.getConvention().getPlugin(JavaPluginConvention.class)
                .getSourceSets()
                .getByName("main");
        return findMainClass(main).orElseThrow(() -> new GradleException(
                "Failed to infer main class, please ensure a main class exists"));
    }

    private static Optional<String> findMainClass(SourceSet sourceSet) {
        return sourceSet.getAllJava().getFiles().stream()
                .flatMap(file -> {
                    String contents = GFileUtils.readFile(file);
                    if (contents.contains("public static void main(")) {
                        Matcher matcher = PACKAGE_PATTERN.matcher(contents);
                        if (matcher.find()) {
                            return Stream.of(matcher.group(1) + "." + Files.getNameWithoutExtension(file.getName()));
                        }
                    }
                    return Stream.empty();
                })
                .findFirst();
    }

    private MainClassResolver() { }
}
