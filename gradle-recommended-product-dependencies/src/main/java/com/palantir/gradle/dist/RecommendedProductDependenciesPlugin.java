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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

public class RecommendedProductDependenciesPlugin implements Plugin<Project> {
    public static final String RESOURCE_PATH =
            RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY + "/product-dependencies.json";

    @Override
    public final void apply(Project project) {
        RecommendedProductDependenciesExtension ext = project.getExtensions()
                .create("recommendedProductDependencies", RecommendedProductDependenciesExtension.class, project);

        project.getPluginManager().withPlugin("java", _plugin -> {
            embedResource(project, ext);
            configureManifest(project, ext);
        });
    }

    private void configureManifest(Project project, RecommendedProductDependenciesExtension ext) {
        TaskProvider<ConfigureProductDependenciesTask> configureProductDependenciesTask = project.getTasks()
                .register("configureProductDependencies", ConfigureProductDependenciesTask.class, cmt -> {
                    cmt.setProductDependencies(ext.getRecommendedProductDependenciesProvider());
                });

        // Ensure that the jar task depends on this wiring task
        project.getTasks().withType(Jar.class).named(JavaPlugin.JAR_TASK_NAME).configure(jar -> {
            jar.dependsOn(configureProductDependenciesTask);
        });
    }

    private void embedResource(Project project, RecommendedProductDependenciesExtension ext) {
        Provider<Directory> dir = project.getLayout().getBuildDirectory().dir("product-dependencies");
        TaskProvider<? extends Task> compilePdeps = project.getTasks()
                .register(
                        "compileRecommendedProductDependencies", CompileRecommendedProductDependencies.class, task -> {
                            task.getRecommendedProductDependencies()
                                    .set(ext.getRecommendedProductDependenciesProvider());
                            task.getOutputFile().set(dir.map(directory -> directory.file(RESOURCE_PATH)));
                        });

        project.getTasks()
                .named(
                        JavaPlugin.PROCESS_RESOURCES_TASK_NAME,
                        processResources -> processResources.dependsOn(compilePdeps));

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.getByName("main").resources(resources -> {
            SourceDirectorySet sourceDir = project.getObjects()
                    .sourceDirectorySet("product-dependencies", "Recommended product dependencies")
                    .srcDir(dir);
            resources.source(sourceDir);
        });
    }
}
