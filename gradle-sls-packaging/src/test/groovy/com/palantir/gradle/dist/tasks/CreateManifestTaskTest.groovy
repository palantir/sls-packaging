/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.tasks


import nebula.test.ProjectSpec
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

class CreateManifestTaskTest extends ProjectSpec {
    def 'Can create CreateManifestTask when product.version is valid SLS version'() {
        when:
        Project project = ProjectBuilder.builder().build()
        project.version = "1.0.0"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)

        then:
        task.getProjectVersion() == "1.0.0"
    }

    def 'Cannot create CreateManifestTask when product.version is invalid SLS version'() {
        given:
        Project project = ProjectBuilder.builder().build()
        project.version = "1.0.0foo"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)

        when:
        task.createManifest()

        then:
        Exception exception = thrown()
        exception.message.contains("Project version must be a valid SLS version: 1.0.0foo. " +
                "Please ensure there's at least one git tag on the repo (e.g. 0.0.0)")
    }

    def 'Cannot create CreateManifestTask when product.version is a commit hash'() {
        given:
        Project project = ProjectBuilder.builder().build()
        project.version = "7895812"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)

        when:
        task.createManifest()

        then:
        Exception exception = thrown()
        exception.message.contains("Project version must be a valid SLS version: 7895812. " +
                "Please ensure there's at least one git tag on the repo (e.g. 0.0.0)")
    }
}
