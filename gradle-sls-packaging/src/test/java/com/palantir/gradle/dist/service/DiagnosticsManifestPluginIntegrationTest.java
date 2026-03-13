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

package com.palantir.gradle.dist.service;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.util.List;
import org.junit.jupiter.api.Test;

@GradlePluginTests
class DiagnosticsManifestPluginIntegrationTest {

    @Test
    void detects_stuff_defined_in_current_project(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java-library").add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            """);

        rootProject
                .directory("src/main/resources/sls-manifest")
                .file("diagnostics.json")
                .overwrite("[{\"type\": \"foo.v1\", \"docs\" : \"This does something\", \"safe\" : false}]");

        gradle.withArgs("mergeDiagnosticsJson", "-is").buildsSuccessfully();

        rootProject
                .buildDir()
                .file("mergeDiagnosticsJson.json")
                .assertThat()
                .content()
                .isEqualTo("""
                    [ {
                      "type" : "foo.v1",
                      "docs" : "This does something",
                      "safe" : false
                    } ]\
                    """);

        InvocationResult result2 =
                gradle.withArgs("mergeDiagnosticsJson", "-is").buildsSuccessfully();

        assertThat(result2).task(":mergeDiagnosticsJson").upToDate();
    }

    @Test
    void detects_stuff_defined_in_sibling_projects(
            GradleInvoker gradle,
            RootProject rootProject,
            SubProject myServer,
            SubProject myProject1,
            SubProject myProject2) {

        List.of(myServer, myProject1, myProject2)
                .forEach(subProject -> subProject.buildGradle().plugins().add("java-library"));

        rootProject.buildGradle().append("""
            subprojects {
                repositories {
                    mavenCentral()
                }
            }
            """);

        myServer.buildGradle().plugins().add("com.palantir.sls-java-service-distribution");

        myServer.buildGradle().append("""
            dependencies {
                implementation project(':myProject1')
                implementation project(':myProject2')
            }
            """);

        myServer.directory("src/main/resources/sls-manifest")
                .file("diagnostics.json")
                .overwrite("[{\"type\": \"foo.v1\", \"docs\" : \"This does something\"}]");

        myProject1
                .directory("src/main/resources/sls-manifest")
                .file("diagnostics.json")
                .overwrite("[{\"type\": \"myproject1.v1\", \"docs\" : \"Who knows what this does\"}]");

        myProject2
                .directory("src/main/resources/sls-manifest")
                .file("diagnostics.json")
                .overwrite("[{\"type\": \"myproject2.v1\", \"docs\" : \"Click me if you dare!\"}]");

        gradle.withArgs("myServer:mergeDiagnosticsJson", "-is").buildsSuccessfully();

        myServer.buildDir()
                .file("mergeDiagnosticsJson.json")
                .assertThat()
                .content()
                .isEqualTo("""
                    [ {
                      "type" : "foo.v1",
                      "docs" : "This does something"
                    }, {
                      "type" : "myproject1.v1",
                      "docs" : "Who knows what this does"
                    }, {
                      "type" : "myproject2.v1",
                      "docs" : "Click me if you dare!"
                    } ]\
                    """);
    }
}
