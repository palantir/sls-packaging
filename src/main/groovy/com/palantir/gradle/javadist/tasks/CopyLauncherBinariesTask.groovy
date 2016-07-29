package com.palantir.gradle.javadist.tasks

import com.palantir.gradle.javadist.JavaDistributionPlugin
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Copy

class CopyLauncherBinariesTask extends Copy {
    CopyLauncherBinariesTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Creates go-java-launcher binaries."

        project.afterEvaluate {
            def zipPath = project.configurations.goJavaLauncherBinaries.find {
                it.name.startsWith("go-java-launcher")
            }
            def zipFile = project.file(zipPath)

            from project.tarTree(zipFile)
            into "${project.buildDir}/scripts"
            includeEmptyDirs = false

            // remove first three levels of directory structure from Tar container
            eachFile { FileCopyDetails fcp ->
                fcp.relativePath = new RelativePath(
                        !fcp.file.isDirectory(),
                        fcp.relativePath.segments[3..-1] as String[])
            }
        }
    }
}
