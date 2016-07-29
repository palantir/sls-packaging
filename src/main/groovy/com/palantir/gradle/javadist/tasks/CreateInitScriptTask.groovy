package com.palantir.gradle.javadist.tasks

import com.palantir.gradle.javadist.util.EmitFiles
import com.palantir.gradle.javadist.JavaDistributionPlugin
import org.gradle.api.tasks.TaskAction

import java.nio.file.Paths

class CreateInitScriptTask extends BaseTask {
    CreateInitScriptTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Generates daemonizing init.sh script."
    }

    @TaskAction
    void createInitScript() {
        EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/init.sh'),
                Paths.get("${project.buildDir}/scripts/init.sh"),
                ['@serviceName@': distributionExtension().serviceName])
                .toFile()
                .setExecutable(true)
    }
}
