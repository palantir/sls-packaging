package com.palantir.gradle.dist.service.tasks

import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import com.palantir.gradle.dist.service.util.EmitFiles
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class CopyYourkitAgentTask extends DefaultTask {

    @Input
    String serviceName

    CopyYourkitAgentTask() {
        group = JavaServiceDistributionPlugin.GROUP_NAME
        description = "Copies YourKit agent"
    }

    @OutputFile
    File getOutputFile() {
        return new File("${project.buildDir}/libs/linux-x86-64/libyjpagent.so")
    }

    @TaskAction
    void copyYourkitAgent() {
        EmitFiles.replaceVars(
                JavaServiceDistributionPlugin.class.getResourceAsStream('/linux-x86-64/libyjpagent.so'),
                getOutputFile().toPath(),
                ['@serviceName@': serviceName])
                .toFile()
                .setExecutable(true)
    }

    void configure(String serviceName) {
        this.serviceName = serviceName
    }
}
