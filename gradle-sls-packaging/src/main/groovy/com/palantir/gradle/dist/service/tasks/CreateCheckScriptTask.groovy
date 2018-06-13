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

package com.palantir.gradle.dist.service.tasks

import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import com.palantir.gradle.dist.service.util.EmitFiles
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class CreateCheckScriptTask extends DefaultTask {

    @Input
    String serviceName

    @Input
    List<String> checkArgs

    CreateCheckScriptTask() {
        group = JavaServiceDistributionPlugin.GROUP_NAME
        description = "Generates healthcheck (service/monitoring/bin/check.sh) script."
    }

    @OutputFile
    File getOutputFile() {
        return new File("${project.buildDir}/monitoring/check.sh")
    }

    @TaskAction
    void createInitScript() {
        if (!checkArgs.empty) {
            EmitFiles.replaceVars(
                    JavaServiceDistributionPlugin.class.getResourceAsStream('/check.sh'),
                    getOutputFile().toPath(),
                    ['@serviceName@': serviceName,
                     '@checkArgs@': checkArgs.iterator().join(' ')])
                    .toFile()
                    .setExecutable(true)
        }
    }

    void configure(String serviceName, List<String> checkArgs) {
        this.serviceName = serviceName
        this.checkArgs = checkArgs
    }
}
