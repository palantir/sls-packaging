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
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Paths

class CreateInitScriptTask extends DefaultTask {
    CreateInitScriptTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Generates daemonizing init.sh script."
    }

    @TaskAction
    void createInitScript() {
        EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/init.sh'),
                Paths.get("${project.buildDir}/scripts/init.sh"),
                ['@serviceName@': project.distributionExtension().serviceName])
                .toFile()
                .setExecutable(true)
    }
}
