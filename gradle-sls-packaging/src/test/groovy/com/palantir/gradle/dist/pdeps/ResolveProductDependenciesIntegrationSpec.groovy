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
import com.palantir.gradle.dist.GradleTestVersions
import com.palantir.gradle.dist.ObjectMappers
import com.palantir.gradle.dist.ProductDependency

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import nebula.test.IntegrationSpec
import nebula.test.dependencies.maven.Pom
import nebula.test.dependencies.repositories.MavenRepo

class ResolveProductDependenciesIntegrationSpec extends IntegrationSpec {
    public static String PDEP = """
    productDependency {
        productGroup = "group1"
        productName = "name1"
        minimumVersion = "1.0.0"
        maximumVersion = "1.3.x"
        recommendedVersion = "1.2.1"
    }
    """.stripIndent(true)

    def setup() {
        buildFile << """
        apply plugin: 'java'
        import ${ProductDependencies.class.getCanonicalName()}
        import ${BaseDistributionExtension.class.getCanonicalName()}
        
        def ext = project.extensions.create("distribution", BaseDistributionExtension, project)
        ProductDependencies.registerProductDependencyTasks(project, ext);
        """.stripIndent(true)
    }

    def '#gradleVersionNumber: consumes declared product dependencies (method: #method)'() {
        setup:
        gradleVersion = gradleVersionNumber
        buildFile << """
            ${method.getBody()}
            distribution {
                ${PDEP}
            }
        """.stripIndent(true)

        when:
        runTasksSuccessfully(':resolveProductDependencies')

        then:
        def manifest = ObjectMappers.readProductDependencyManifest(
                file('build/resolved-pdeps/pdeps-manifest.json'))
        !manifest.productDependencies().isEmpty()

        where:
        [gradleVersionNumber, method] << [
                GradleTestVersions.GRADLE_VERSIONS,
                DependencyMethod.values()
        ].combinations()
    }

    def '#gradleVersionNumber: discovers project dependencies without compilation (method: #method)'() {
        given:
        gradleVersion = gradleVersionNumber
        addSubproject('child', """
        apply plugin: 'java'
        apply plugin: 'com.palantir.recommended-product-dependencies'
        
        recommendedProductDependencies {
            ${PDEP}
        }
        """.stripIndent(true))
        buildFile << """
        ${method.getBody()}
        dependencies {
            implementation project('child')
        }
        """.stripIndent(true)

        when:
        def result = runTasksSuccessfully(':resolveProductDependencies')

        then:
        !result.wasExecuted(':child:jar')
        def manifest = ObjectMappers.readProductDependencyManifest(
                file('build/resolved-pdeps/pdeps-manifest.json'))
        !manifest.productDependencies().isEmpty()

        where:
        [gradleVersionNumber, method] << [
                GradleTestVersions.GRADLE_VERSIONS,
                DependencyMethod.values()
        ].combinations()
    }

    def '#gradleVersionNumber: discovers external dependencies (method: #method)'() {
        given:
        gradleVersion = gradleVersionNumber
        def mavenRepo = generateMavenRepo("a", "a", "1.0")

        // depends on group:name:[1.0.0, 1.x.x]:1.2.0
        copyResource(mavenRepo, "/a-1.0.jar", "a/a/1.0/a-1.0.jar")

        buildFile << """
        repositories {
            maven {url "file:///${mavenRepo.getAbsolutePath()}"}
        }
        
        ${method.getBody()}
        
        dependencies {
            implementation 'a:a:1.0'
        }
        """.stripIndent(true)

        when:
        runTasksSuccessfully(':resolveProductDependencies')

        then:
        def manifest = ObjectMappers.readProductDependencyManifest(
                file('build/resolved-pdeps/pdeps-manifest.json'))
        !manifest.productDependencies().isEmpty()

        where:
        [gradleVersionNumber, method] << [
                GradleTestVersions.GRADLE_VERSIONS,
                DependencyMethod.values()
        ].combinations()
    }

    def '#gradleVersionNumber: handles jars without manifest (method: #method)'() {
        given:
        gradleVersion = gradleVersionNumber
        def mavenRepo = generateMavenRepo("missingmanifest", "missingmanifest", "1.0")

        copyResource(
                mavenRepo,
                "/missing-manifest.jar",
                "missingmanifest/missingmanifest/1.0/missingmanifest-1.0.jar")

        buildFile << """
        repositories {
            maven {url "file:///${mavenRepo.getAbsolutePath()}"}
        }
        
        ${method.getBody()}
        
        dependencies {
            implementation 'missingmanifest:missingmanifest:1.0'
        }
        """.stripIndent(true)

        when:
        runTasksSuccessfully(':resolveProductDependencies')

        then:
        def manifest = ObjectMappers.readProductDependencyManifest(
                file('build/resolved-pdeps/pdeps-manifest.json'))
        manifest.productDependencies().isEmpty()

        where:
        [gradleVersionNumber, method] << [
                GradleTestVersions.GRADLE_VERSIONS,
                DependencyMethod.values()
        ].combinations()
    }

    def "#gradleVersionNumber: can exclude discovered product dependencies by module (method: #method)"() {
        given:
        gradleVersion = gradleVersionNumber
        def groupPdep = new ProductDependency("group", "name", "1.0.0", "1.x.x", "1.2.0")
        def group1Pdep = new ProductDependency("group1", "name1", "1.0.0", "1.3.x", "1.2.1")

        def mavenRepo = generateMavenRepo("a", "a", "1.0")

        // depends on group:name:[1.0.0, 1.x.x]:1.2.0
        copyResource(mavenRepo, "/a-1.0.jar", "a/a/1.0/a-1.0.jar")

        buildFile << """
            repositories {
                maven {url "file:///${mavenRepo.getAbsolutePath()}"}
            }

            ${method.getBody()}

            dependencies {
                implementation project('localApi')
            }
        """.stripIndent(true)

        addSubproject("localApi", """
            repositories {
                maven {url "file:///${mavenRepo.getAbsolutePath()}"}
            }
            
            apply plugin: 'java'
            apply plugin: 'com.palantir.recommended-product-dependencies'
            
            dependencies {
                implementation 'a:a:1.0'
            }
            
            recommendedProductDependencies {
                ${PDEP}
            }
        """.stripIndent(true))

        when: 'we try to resolve product dependencies *without* excludes'
        runTasksSuccessfully(':resolveProductDependencies')

        then: 'we get both product dependencies'
        def manifest = ObjectMappers.readProductDependencyManifest(
                file('build/resolved-pdeps/pdeps-manifest.json'))

        manifest.productDependencies().toSet() == [groupPdep, group1Pdep] as Set
        when: 'we exclude `a:a` and try to resolve product dependencies'
        buildFile << """
            configurations {
                productDependencyDiscovery {
                    exclude group: 'a', module: 'a'
                }
            } 
        """.stripIndent(true)
        runTasksSuccessfully(':resolveProductDependencies')

        then: '`group:name` is no longer a product dependency (brought in from `a:a`)'
        def manifestAfterExcludes = ObjectMappers.readProductDependencyManifest(
                file('build/resolved-pdeps/pdeps-manifest.json'))
        manifestAfterExcludes.productDependencies().toSet() == [group1Pdep] as Set

        where:
        [gradleVersionNumber, method] << [GradleTestVersions.GRADLE_VERSIONS, DependencyMethod.values()].combinations()
    }

    def '#gradleVersionNumber: resolveProductDependencies and processResources work together'() {
        setup:
        gradleVersion = gradleVersionNumber
        
        // this is a strange setup that really shouldn't happen in a real repo - a project shouldn't be both an API
        // jar and a distribution.  But in case it does happen we want to make sure there are no accidental
        // connections between the tasks.
        when:
        //language=gradle
        buildFile.text = '''
            apply plugin: 'java'
            apply plugin: 'com.palantir.recommended-product-dependencies'
            apply plugin: 'com.palantir.sls-asset-distribution'
        '''.stripIndent(true)

        then:
        runTasksSuccessfully('resolveProductDependencies', 'processResources')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    private static void copyResource(File mavenRepo, String resource, String relativePath) {
        def target = new File(mavenRepo, relativePath).toPath()
        Files.createDirectories(target.getParent())
        Files.copy(
                ResolveProductDependenciesIntegrationSpec.class.getResourceAsStream(resource),
                target,
                StandardCopyOption.REPLACE_EXISTING)
    }

    private File generateMavenRepo(String group, String artifact, String version) {
        def mavenRepo = new MavenRepo(
                root: new File(projectDir, "build/testrepogen/mavenrepo"),
                poms: [new Pom(group, artifact, version)] as Set)
        mavenRepo.generate()
        return mavenRepo.root
    }

    private enum DependencyMethod {
        EXTENSION_SETTER("extension setter", "ext.setProductDependenciesConfig(configurations.runtimeClasspath)"),
        CONFIGURATION_EXTENDS_FROM("extends from", "configurations.productDependencyDiscovery.extendsFrom(configurations.runtimeClasspath)"),
        CONSUMABLE_CONFIGURATION_DEPENDENCY("consumable configuration dependency", """
            dependencies {
              productDependencyDiscovery project(path: project.path, configuration: 'runtimeElements')
            }
        """.stripIndent(true))

        private final String name
        private final String body

        DependencyMethod(String name, String body) {
            this.name = name
            this.body = body
        }

        String getBody() {
            return body
        }

        @Override
        String toString() {
            return name
        }
    }
}
