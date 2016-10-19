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

import com.palantir.gradle.javadist.JavaDistributionPlugin
import org.gradle.api.Project
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Copy

class CopyLauncherBinariesTask {
    public static Copy createCopyLauncherBinariesTask(Project project, String taskName) {
        def copyLauncherBinaries = project.tasks.create(taskName, Copy.class)
        copyLauncherBinaries.group = JavaDistributionPlugin.GROUP_NAME
        copyLauncherBinaries.description = "Creates go-java-launcher binaries."

        def zipPath = project.configurations.goJavaLauncherBinaries.find {
            it.name.startsWith("go-java-launcher")
        }

        def zipFile = project.file(zipPath)

        copyLauncherBinaries.from project.tarTree(zipFile)
        copyLauncherBinaries.into "${project.buildDir}/scripts"
        copyLauncherBinaries.includeEmptyDirs = false

        // remove first three levels of directory structure from Tar container
        copyLauncherBinaries.eachFile { FileCopyDetails fcp ->
            fcp.relativePath = new RelativePath(
                    !fcp.file.isDirectory(),
                    fcp.relativePath.segments[3..-1] as String[])
        }
        return copyLauncherBinaries
    }
}
