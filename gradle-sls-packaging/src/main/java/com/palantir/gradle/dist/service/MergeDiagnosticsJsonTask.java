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

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.palantir.gradle.dist.tasks.CreateManifestTask;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class MergeDiagnosticsJsonTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getClasspath();

    @OutputFile
    public abstract RegularFileProperty getOutputJsonFile();

    @TaskAction
    public final void taskAction() {
        List<ObjectNode> aggregated = getClasspath().getFiles().stream()
                .flatMap(file -> Diagnostics.parse(getProject(), file).stream())
                .distinct()
                .sorted(Comparator.comparing(node -> node.get("type").asText()))
                .collect(Collectors.toList());

        File out = getOutputJsonFile().getAsFile().get();
        try {
            CreateManifestTask.jsonMapper.writeValue(out, aggregated);
        } catch (IOException e) {
            throw new GradleException("Failed to write " + out, e);
        }
    }
}
