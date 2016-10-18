/*
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.javadist.tasks

import com.palantir.gradle.javadist.util.EmitFiles
import com.palantir.gradle.javadist.JavaDistributionPlugin
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask

import java.nio.file.Paths

class CreateCheckScriptTask extends DefaultTask {
    CreateCheckScriptTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Generates healthcheck (service/monitoring/bin/check.sh) script."
    }

    @TaskAction
    void createInitScript() {
        if (!project.distributionExtension().checkArgs.empty) {
            EmitFiles.replaceVars(
                    JavaDistributionPlugin.class.getResourceAsStream('/check.sh'),
                    Paths.get("${project.buildDir}/monitoring/check.sh"),
                    ['@serviceName@': project.distributionExtension().serviceName,
                     '@checkArgs@': project.distributionExtension().checkArgs.iterator().join(' ')])
                    .toFile()
                    .setExecutable(true)
        }
    }
}
