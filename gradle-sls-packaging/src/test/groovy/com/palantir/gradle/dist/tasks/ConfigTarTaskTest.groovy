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

class ConfigTarTaskTest extends ProjectSpec {
    def 'configTar task fails for invalid product types'() {
        when:
        Project project = ProjectBuilder.builder().withName("foo").build()
        ConfigTarTask.createConfigTarTask(project, "configTar", "foo.bar")

        then:
        def err = thrown(IllegalArgumentException)
        err.message.contains("Product type must end with")
    }
}
