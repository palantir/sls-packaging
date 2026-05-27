/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.AdditionallyRunWithGradle;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
@AdditionallyRunWithGradle("9.3.1")
class ProductDependencyClosureGcvSubprojectIntegrationTest {

    @BeforeEach
    void setup(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.consistent-versions");
        rootProject.file("versions.props").createEmpty();
        rootProject.file("versions.lock").createEmpty();
    }

    @Test
    void productDependency_minimumVersionFrom_resolves_via_gcv_under_parallel(
            GradleInvoker gradle, SubProject child) {
        child.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");
        child.buildGradle().append("""
            distribution {
                serviceName 'service-name'
                mainClass 'dummy.Main'
                productDependency {
                    productGroup = 'group'
                    productName = 'name'
                    minimumVersionFrom = 'group:name'
                    maximumVersion = '1.x.x'
                }
            }
            """);

        InvocationResult result = gradle.withArgs(":child:resolveProductDependencies", "--parallel")
                .buildsWithFailure();
        assertThat(result)
                .output()
                .contains("Unable to resolve minimumVersionFrom 'group:name' for product dependency group:name");
    }
}
