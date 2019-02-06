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


import com.palantir.gradle.dist.RawProductDependency
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
        exception.message.contains("Project version must be a valid SLS version: 1.0.0foo")
    }

    def "Fails if user declares dependency on the same product"() {
        project.version = "1.0.0"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)
        task.serviceGroup = "serviceGroup"
        task.serviceName = "serviceName"
        task.productDependencies = [
                new RawProductDependency("serviceGroup", "serviceName", "1.1.0", "1.x.x", "1.2.0"),
        ]

        when:
        task.createManifest()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains 'Invalid for product to declare an explicit dependency on itself'

    }
}
