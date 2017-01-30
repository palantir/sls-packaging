/*
 * Copyright 2015 Palantir Technologies
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
package com.palantir.gradle.dist.service.tasks

import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec

class RunTask {

    static JavaExec createRunTask(Project project, String taskName) {
        return project.tasks.create(taskName, JavaExec) { t ->
            t.group = JavaServiceDistributionPlugin.GROUP_NAME
            t.description = "Runs the specified project using configured mainClass and with default args."
            t.classpath project.sourceSets.main.runtimeClasspath
        }
    }

    static void configure(JavaExec runTask, String mainClass, List<String> args, List<String> defaultJvmOpts) {
        runTask.configure {
            setMain(mainClass)
            if (!args.isEmpty()) {
                setArgs(args)
            }
            setJvmArgs(defaultJvmOpts)
        }
    }
}
