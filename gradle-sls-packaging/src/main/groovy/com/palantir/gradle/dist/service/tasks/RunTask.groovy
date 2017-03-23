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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class RunTask extends DefaultTask {

    @Input
    def Closure<String> mainClass

    @Input
    def Closure<List<String>> args

    @Input
    def Closure<List<String>> defaultJvmOpts

    @TaskAction
    def exec() {
        project.javaexec({
            main mainClass.call()
            args args.call()
            jvmArgs defaultJvmOpts.call()
            classpath project.sourceSets.main.runtimeClasspath
        })
    }

}
