/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.arbitrary.ArbitraryFile;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.maven.MavenArtifact;
import com.palantir.gradle.testing.maven.MavenRepo;
import com.palantir.gradle.testing.project.RootProject;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class RecommendedProductDependenciesPluginIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.recommended-product-dependencies");
    }

    @Test
    void manifest_includes_recommended_product_dependencies(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        rootProject.buildGradle().append("""
            recommendedProductDependencies {
                productDependency {
                    productGroup = 'group'
                    productName = 'name'
                    minimumVersion = '1.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = '1.2.3'
                    optional = true
                }
            }
            """);

        gradle.withArgs(":jar").buildsSuccessfully();

        ArbitraryFile jarFile = rootProject.buildDir().file("libs/root.jar");
        jarFile.assertThat().as("jar file is generated").exists();

        ProductDependency dep = Iterables.getOnlyElement(
                readRecommendedProductDeps(jarFile.path().toFile()).recommendedProductDependencies());
        assertThat(dep.getProductGroup()).isEqualTo("group");
        assertThat(dep.getProductName()).isEqualTo("name");
        assertThat(dep.getMinimumVersion()).isEqualTo("1.0.0");
        assertThat(dep.getMaximumVersion()).isEqualTo("1.x.x");
        assertThat(dep.getRecommendedVersion()).isEqualTo("1.2.3");
        assertThat(dep.getOptional()).isTrue();
    }

    @Test
    void sources_jar_runs_compile_recommended_product_dependencies(GradleInvoker gradle, RootProject rootProject) {
        // language=groovy
        rootProject.buildGradle().append("""
            recommendedProductDependencies {
                productDependency {
                    productGroup = 'group'
                    productName = 'name'
                    minimumVersion = '1.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = '1.2.3'
                }
            }

            java {
               withSourcesJar()
            }
            """);

        rootProject.mainSourceSet().java().writeClass("""
            public class Main {
                public static void main(String[] args) { }
            }
            """);

        InvocationResult result = gradle.withArgs(":sourcesJar").buildsSuccessfully();

        assertThat(result).task(":compileRecommendedProductDependencies").succeeded();
        rootProject
                .buildDir()
                .file("libs/root-sources.jar")
                .assertThat()
                .as("sources jar is generated")
                .exists();
    }

    @Test
    void jar_includes_recommended_product_dependencies(GradleInvoker gradle, RootProject rootProject)
            throws IOException {
        rootProject.buildGradle().append("""
            recommendedProductDependencies {
                productDependency {
                    productGroup = 'group'
                    productName = 'name'
                    minimumVersion = '1.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = '1.2.3'
                }
            }
            """);

        InvocationResult result = gradle.withArgs(":jar").buildsSuccessfully();

        assertThat(result).task(":compileRecommendedProductDependencies").succeeded();
        ArbitraryFile jarFile = rootProject.buildDir().file("libs/root.jar");
        jarFile.assertThat().as("jar file is generated").exists();

        ProductDependency dep = Iterables.getOnlyElement(
                readRecommendedProductDeps(jarFile.path().toFile()).recommendedProductDependencies());
        assertThat(dep.getProductGroup()).isEqualTo("group");
        assertThat(dep.getProductName()).isEqualTo("name");
        assertThat(dep.getMinimumVersion()).isEqualTo("1.0.0");
        assertThat(dep.getMaximumVersion()).isEqualTo("1.x.x");
        assertThat(dep.getRecommendedVersion()).isEqualTo("1.2.3");
    }

    @Test
    void works_with_consistent_versions(GradleInvoker gradle, RootProject rootProject, MavenRepo repo)
            throws IOException {
        repo.publish(MavenArtifact.of("group:name:1.0.0"));
        rootProject.buildGradle().plugins().add("com.palantir.consistent-versions");
        rootProject.buildGradle().withMavenRepo(repo);
        rootProject.buildGradle().append("""
            dependencies {
                // just so it becomes available to gradle-consistent-versions' getVersion
                implementation 'group:name:1.0.0'
            }

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'group'
                    productName = 'name'
                    minimumVersion = getVersion('group:name')
                    maximumVersion = '1.x.x'
                }
            }
            """);

        InvocationResult result = gradle.withArgs("--write-locks", ":jar").buildsSuccessfully();

        assertThat(result).task(":compileRecommendedProductDependencies").succeeded();
        ArbitraryFile jarFile = rootProject.buildDir().file("libs/root.jar");
        jarFile.assertThat().as("jar file is generated").exists();

        ProductDependency dep = Iterables.getOnlyElement(
                readRecommendedProductDeps(jarFile.path().toFile()).recommendedProductDependencies());
        assertThat(dep.getProductGroup()).isEqualTo("group");
        assertThat(dep.getProductName()).isEqualTo("name");
        assertThat(dep.getMinimumVersion()).isEqualTo("1.0.0");
        assertThat(dep.getMaximumVersion()).isEqualTo("1.x.x");
    }

    private static RecommendedProductDependencies readRecommendedProductDeps(File jarFile) throws IOException {
        try (ZipFile zf = new ZipFile(jarFile)) {
            ZipEntry resource = zf.getEntry(
                    RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY + "/product-dependencies.json");
            return CompileRecommendedProductDependencies.MAPPER.readValue(
                    zf.getInputStream(resource), RecommendedProductDependencies.class);
        }
    }
}
