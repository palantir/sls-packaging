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

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

public class RecommendedProductDependenciesPlugin implements Plugin<Project> {

    @Override
    public final void apply(Project project) {
        project.getPlugins().apply("java");
        final RecommendedProductDependenciesExtension ext = project.getExtensions()
                .create("recommendedProductDependencies", RecommendedProductDependenciesExtension.class, project);

        TaskProvider<DefaultTask> configureProductDependencies =
                project.getTasks().register("configureProductDependencies", DefaultTask.class);

        project.getTasks().withType(Jar.class).configureEach(jar -> {
            project.getTasks()
                    .register("configureProductDependencies_" + jar.getName(), ConfigureProductDependenciesTask.class)
                    .configure(configureProductDependenciesTask -> {
                        configureProductDependencies.configure(
                                task -> task.dependsOn(configureProductDependenciesTask));
                        configureProductDependenciesTask.setProductDependencies(
                                ext.getRecommendedProductDependenciesProvider());
                    });
        });
    }
}
