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
package com.palantir.gradle.javadist.tasks

import com.palantir.gradle.javadist.DistributionExtension
import com.palantir.gradle.javadist.JavaDistributionPlugin
import org.gradle.api.tasks.JavaExec

class RunTask extends JavaExec {

    RunTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Runs the specified project using configured mainClass and with default args."

        project.afterEvaluate {
            setClasspath(project.sourceSets.main.runtimeClasspath)
            setMain(distributionExtension().mainClass)
            if (!distributionExtension().args.isEmpty()) {
                setArgs(distributionExtension().args)
            }
            setJvmArgs(distributionExtension().getDefaultJvmOpts())
        }
    }

    DistributionExtension distributionExtension() {
        return project.extensions.findByType(DistributionExtension)
    }
}
