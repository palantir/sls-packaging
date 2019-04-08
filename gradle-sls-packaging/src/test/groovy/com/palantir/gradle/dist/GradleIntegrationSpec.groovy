/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist

import nebula.test.IntegrationTestKitSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator
import nebula.test.multiproject.MultiProjectIntegrationHelper

class GradleIntegrationSpec extends IntegrationTestKitSpec {
    protected MultiProjectIntegrationHelper helper

    def setup() {
        keepFiles = true
        System.setProperty("ignoreDeprecations", "true")
        settingsFile.createNewFile()
        helper = new MultiProjectIntegrationHelper(getProjectDir(), settingsFile)
    }

    protected boolean fileExists(String path) {
        new File(projectDir, path).exists()
    }

    GradleDependencyGenerator generateMavenRepo(String... graph) {
        DependencyGraph dependencyGraph = new DependencyGraph(graph)
        GradleDependencyGenerator generator = new GradleDependencyGenerator(dependencyGraph)
        generator.generateTestMavenRepo()
        return generator
    }
}
