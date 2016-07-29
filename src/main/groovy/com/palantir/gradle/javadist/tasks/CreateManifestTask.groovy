package com.palantir.gradle.javadist.tasks

import com.palantir.gradle.javadist.util.EmitFiles
import com.palantir.gradle.javadist.JavaDistributionPlugin
import org.gradle.api.tasks.TaskAction

import java.nio.file.Paths

class CreateManifestTask extends BaseTask {
    CreateManifestTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Generates a simple yaml file describing the package content."
    }

    @TaskAction
    void createManifest() {
        EmitFiles.replaceVars(
                CreateManifestTask.class.getResourceAsStream('/manifest.yml'),
                Paths.get("${project.buildDir}/deployment/manifest.yml"),
                ['@serviceName@'   : distributionExtension().serviceName,
                 '@serviceVersion@': String.valueOf(project.version)])
    }
}
