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

package com.palantir.gradle.dist;

import com.palantir.gradle.dist.tasks.ConfigureProductDependenciesTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

public class RecommendedProductDependenciesPlugin implements Plugin<Project> {

    public final void apply(final Project project) {
        project.getPlugins().apply("java");
        final RecommendedProductDependenciesExtension ext = project.getExtensions()
                .create("recommendedProductDependencies", RecommendedProductDependenciesExtension.class, project);

        TaskProvider<?> configureProductDependenciesTask = project.getTasks()
                .register("configureProductDependencies", ConfigureProductDependenciesTask.class, cmt -> {
                    cmt.setProductDependencies(ext.getRecommendedProductDependenciesProvider());
                });

        // Ensure that the jar task depends on this wiring task
        project.getTasks().withType(Jar.class).named(JavaPlugin.JAR_TASK_NAME).configure(jar -> {
            jar.dependsOn(configureProductDependenciesTask);
        });
    }
}
