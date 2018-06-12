/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar

/**
 * Produces a JAR whose manifest's {@code Class-Path} entry lists exactly the JARs produced by the project's runtime
 * classpath.
 */
class ManifestClasspathJarTask {

    static Jar createManifestClasspathJarTask(Project project, String taskName) {
        return project.tasks.create(taskName, Jar) { t ->
            t.group = JavaServiceDistributionPlugin.GROUP_NAME
            t.description = "Creates a jar containing a Class-Path manifest entry specifying the classpath using pathing " +
                    "jar rather than command line argument on Windows, since Windows path sizes are limited."
            t.appendix = 'manifest-classpath'

            t.doFirst {
                t.manifest.attributes(
                        "Class-Path": project.files(project.configurations.runtimeClasspath)
                                .collect { it.getName() }
                                .join(' ') + ' ' + t.archiveName
                )
            }
        }
    }
}
