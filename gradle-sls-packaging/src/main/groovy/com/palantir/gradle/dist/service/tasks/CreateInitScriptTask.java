/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service.tasks;

import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import com.palantir.gradle.dist.service.util.EmitFiles;
import java.io.File;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class CreateInitScriptTask extends DefaultTask {
    private final Property<String> serviceName = getProject().getObjects().property(String.class);

    // TODO(forozco): Use RegularFileProperty once our minimum supported version is 5.0
    private File outputFile = new File(getProject().getBuildDir(), "scripts/init.sh");

    @Input
    public final Property<String> getServiceName() {
        return serviceName;
    }

    @OutputFile
    public final File getOutputFile() {
        return outputFile;
    }

    public final void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    @TaskAction
    final void createInitScript() {
        EmitFiles.replaceVars(
                JavaServiceDistributionPlugin.class.getResourceAsStream("/init.sh"),
                getOutputFile().toPath(),
                ImmutableMap.of("@serviceName@", serviceName.get()))
                .toFile()
                .setExecutable(true);
    }
}
