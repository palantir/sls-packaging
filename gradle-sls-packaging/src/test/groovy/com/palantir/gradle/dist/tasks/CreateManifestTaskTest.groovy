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

import com.palantir.gradle.dist.RecommendedProductDependencies
import com.palantir.gradle.dist.RecommendedProductDependency
import java.nio.file.Files
import nebula.test.ProjectSpec

class CreateManifestTaskTest extends ProjectSpec {
    def 'Can create CreateManifestTask when product.version is valid SLS version'() {
        when:
        project.version = "1.0.0"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)

        then:
        task.getProjectVersion() == "1.0.0"
    }

    def 'Cannot create CreateManifestTask when product.version is invalid SLS version'() {
        when:
        project.version = "1.0.0foo"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)
        task.getProjectVersion() == "1.0.0"

        then:
        IllegalArgumentException exception = thrown()
        exception.message == "Project version must be a valid SLS version: 1.0.0foo"
    }

    def 'Can read pdep'() {
        project.version = "1.0.0"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)
        def artifact = projectDir.toPath().resolve("pdep-1.0.jar")
        Files.copy(CreateManifestTaskTest.getResourceAsStream("/pdep-1.0.jar"), artifact)

        def expected = CreateManifestTask.jsonMapper.convertValue(
                [
                        "product-group": "group",
                        "product-name": "name",
                        "minimum-version": "1.5.0",
                        "maximum-version": "1.8.x",
                        "recommended-version": "1.7.0",
                ],
                RecommendedProductDependency)

        expect:
        task.readProductDepsFromPdepFile("", artifact.toFile()) == RecommendedProductDependencies
                .builder()
                .addRecommendedProductDependencies(expected)
                .build()
    }
}
