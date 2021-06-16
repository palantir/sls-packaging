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

package com.palantir.gradle.dist.pdeps

import com.palantir.gradle.dist.BaseDistributionExtension
import com.palantir.gradle.dist.ObjectMappers
import nebula.test.IntegrationSpec
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator

class ResolveProductDependenciesIntegrationSpec extends IntegrationSpec {
    private static String PDEP = """
    productDependency {
        productGroup = "group1"
        productName = "name1"
        minimumVersion = "1.0.0"
        maximumVersion = "1.3.x"
        recommendedVersion = "1.2.1"
    }
    """.stripIndent()

    def setup() {
        buildFile << """
        apply plugin: 'java'
        import ${ProductDependencies.class.getCanonicalName()}
        import ${BaseDistributionExtension.class.getCanonicalName()}
        
        def ext = project.extensions.create("base", BaseDistributionExtension, project)
        ext.setProductDependenciesConfig(configurations.runtimeClasspath)
        ProductDependencies.registerProductDependencyTasks(project, ext);
        """.stripIndent()
    }

    def 'consumes declared product dependencies'() {
        setup:
        buildFile << """
            base {
                ${PDEP}
            }
        """.stripIndent()

        when:
        def buildResult = runTasks(':resolveProductDependencies')

        then:
        def manifest = ObjectMappers.readRecommendedProductDependencies(
                file('build/product-dependencies/pdeps-manifest.json'))
        !manifest.productDependencies().isEmpty()
    }

    def 'discovers project dependencies without compilation'() {
        given:
        addSubproject('child', """
        apply plugin: 'java'
        apply plugin: 'com.palantir.recommended-product-dependencies'
        
        recommendedProductDependencies {
            ${PDEP}
        }
        """.stripIndent())
        buildFile << """
        dependencies {
            implementation project('child')
        }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully(':resolveProductDependencies')

        then:
        !result.wasExecuted(':child:jar')
        def manifest = ObjectMappers.readRecommendedProductDependencies(
                file('build/product-dependencies/pdeps-manifest.json'))
        !manifest.productDependencies().isEmpty()
    }

    def 'discovers external dependencies'() {
        given:
        GradleDependencyGenerator generator = new GradleDependencyGenerator(
                new DependencyGraph("a:a:1.0"), new File(projectDir, "build/testrepogen").toString())
        def mavenRepo = generator.generateTestMavenRepo()

        // depends on group:name:[1.0.0, 1.x.x]:1.2.0
        Files.copy(
                ResolveProductDependenciesIntegrationSpec.class.getResourceAsStream("/a-1.0.jar"),
                new File(mavenRepo, "a/a/1.0/a-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)

        buildFile << """
        repositories {
            maven {url "file:///${mavenRepo.getAbsolutePath()}"}
        }
        
        dependencies {
            implementation 'a:a:1.0'
        }
        """.stripIndent()

        when:
        def result = runTasksSuccessfully(':resolveProductDependencies')

        then:
        def manifest = ObjectMappers.readRecommendedProductDependencies(
                file('build/product-dependencies/pdeps-manifest.json'))
        !manifest.productDependencies().isEmpty()
    }

}
