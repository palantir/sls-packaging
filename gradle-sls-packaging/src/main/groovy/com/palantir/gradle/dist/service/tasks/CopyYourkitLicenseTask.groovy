package com.palantir.gradle.dist.service.tasks

import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path

class CopyYourkitLicenseTask extends DefaultTask {

    CopyYourkitLicenseTask() {
        group = JavaServiceDistributionPlugin.GROUP_NAME
        description = "Copies YourKit license"
    }

    @OutputFile
    File getOutputFile() {
        return new File("${project.buildDir}/libs/linux-x86-64/yourkit-license.txt")
    }

    @TaskAction
    void copyYourkitLicense() {
        InputStream src = JavaServiceDistributionPlugin.class.getResourceAsStream('/yourkit-license.txt')
        Path dest = getOutputFile().toPath()
        dest.getParent().toFile().mkdirs()
        Files.write(dest, src.getBytes())
    }
}