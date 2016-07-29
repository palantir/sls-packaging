package com.palantir.gradle.javadist.tasks

import com.palantir.gradle.javadist.util.EmitFiles
import com.palantir.gradle.javadist.JavaDistributionPlugin
import org.gradle.api.tasks.TaskAction

import java.nio.file.Paths

class CreateCheckScriptTask extends BaseTask {
    CreateCheckScriptTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Generates healthcheck (service/monitoring/bin/check.sh) script."
    }

    @TaskAction
    void createInitScript() {
        if (!distributionExtension().checkArgs.empty) {
            EmitFiles.replaceVars(
                    JavaDistributionPlugin.class.getResourceAsStream('/check.sh'),
                    Paths.get("${project.buildDir}/monitoring/check.sh"),
                    ['@serviceName@': distributionExtension().serviceName,
                     '@checkArgs@': distributionExtension().checkArgs.iterator().join(' ')])
                    .toFile()
                    .setExecutable(true)
        }
    }
}
