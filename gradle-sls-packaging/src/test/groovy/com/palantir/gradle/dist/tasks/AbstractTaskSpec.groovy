/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import java.nio.file.Files
import com.palantir.gradle.dist.GradleIntegrationSpec;
import java.nio.file.StandardCopyOption;
import nebula.test.dependencies.DependencyGraph;
import nebula.test.dependencies.GradleDependencyGenerator;

abstract class AbstractTaskSpec extends GradleIntegrationSpec {
    static final String STANDARD_PRODUCT_DEPENDENCY = '''
        productDependency {
            productGroup = 'group'
            productName = 'name'
            minimumVersion = '1.0.0'
            maximumVersion = '1.x.x'
            recommendedVersion = '1.2.0'
        }
        '''

    File mavenRepo

    def setup() {
        generateDependencies()
        buildFile << """
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                maven {url "file:///${mavenRepo.getAbsolutePath()}"}
            }

            project.version = '1.0.0'
            distribution {
                serviceName 'serviceName'
                serviceGroup 'serviceGroup'
            }
        """.stripIndent()
    }

    void generateDependencies() {
        DependencyGraph dependencyGraph = new DependencyGraph(
                "a:a:1.0 -> b:b:1.0|c:c:1.0", "b:b:1.0", "c:c:1.0", "d:d:1.0", "e:e:1.0",
                "pdep:pdep:1.0")
        GradleDependencyGenerator generator = new GradleDependencyGenerator(
                dependencyGraph, new File(projectDir, "build/testrepogen").toString())
        mavenRepo = generator.generateTestMavenRepo()


        // depends on group:name:[1.0.0, 1.x.x]:1.2.0
        Files.copy(
                ResolveProductDependenciesTaskIntegrationSpec.class.getResourceAsStream("/a-1.0.jar"),
                new File(mavenRepo, "a/a/1.0/a-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        // depends on group:name2:[2.0.0, 2.x.x]:2.2.0
        Files.copy(
                ResolveProductDependenciesTaskIntegrationSpec.class.getResourceAsStream("/b-1.0.jar"),
                new File(mavenRepo, "b/b/1.0/b-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        // Make d.jar a duplicate of b.jar
        Files.copy(
                ResolveProductDependenciesTaskIntegrationSpec.class.getResourceAsStream("/b-1.0.jar"),
                new File(mavenRepo, "d/d/1.0/d-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        // e-1.0.jar declares group:name2:[2.1.0, 2.6.x]:2.2.0
        Files.copy(
                ResolveProductDependenciesTaskIntegrationSpec.class.getResourceAsStream("/b-duplicate-different-versions-1.0.jar"),
                new File(mavenRepo, "e/e/1.0/e-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
    }

    void addStandardProductDependency(boolean optional = false) {
        buildFile << """
            distribution {
                $STANDARD_PRODUCT_DEPENDENCY
            }
            """.stripIndent()
    }
}
