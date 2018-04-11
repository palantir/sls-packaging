package com.palantir.gradle.dist.service.tasks

import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path

class CopyYourkitAgentTask extends DefaultTask {

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
        InputStream src = JavaServiceDistributionPlugin.class.getResourceAsStream('/linux-x86-64/libyjpagent.so')
        Path dest = getOutputFile().toPath()
        dest.getParent().toFile().mkdirs()
        Files.write(dest, src.getBytes())
    }
}
