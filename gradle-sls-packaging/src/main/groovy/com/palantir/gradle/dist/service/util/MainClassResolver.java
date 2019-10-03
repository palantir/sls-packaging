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

import com.google.common.collect.Iterables;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

public final class MainClassResolver {
    public static String resolveMainClass(Project project) {
        SourceSet main = project.getConvention().getPlugin(JavaPluginConvention.class)
                .getSourceSets()
                .getByName("main");
        Set<Path> javaFilesWithMainMethods = main.getAllSource().getSrcDirs().stream()
                .filter(File::exists)
                .map(File::toPath)
                .flatMap(sourceDir -> allJavaFilesIn(sourceDir)
                        .filter(javaFile -> anyLinesInFileContain(javaFile, "public static void main("))
                        .map(sourceDir::relativize))
                .collect(Collectors.toSet());

        if (javaFilesWithMainMethods.size() != 1) {
            throw new RuntimeException(String.format(
                    "Expecting to find exactly one main method, however we found %s of them in:\n%s\n",
                    javaFilesWithMainMethods.size(),
                    javaFilesWithMainMethods.stream()
                            .map(Object::toString)
                            .sorted()
                            .collect(Collectors.joining("\n"))));
        }

        return Iterables.getOnlyElement(javaFilesWithMainMethods).toString()
                .replace(".java", "")
                .replace(File.separatorChar, '.');
    }

    private static boolean anyLinesInFileContain(Path path, String text) {
        try (Stream<String> lines = java.nio.file.Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.anyMatch(line -> line.contains(text));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<Path> allJavaFilesIn(Path rootPath) {
        try (Stream<Path> paths = java.nio.file.Files.walk(rootPath, Integer.MAX_VALUE)) {
            return paths
                    .filter(path -> Files.isRegularFile(path))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toSet())
                    .stream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MainClassResolver() {}
}
