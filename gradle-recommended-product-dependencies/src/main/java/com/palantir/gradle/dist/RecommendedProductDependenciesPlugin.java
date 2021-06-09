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
import org.gradle.api.file.Directory;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

public class RecommendedProductDependenciesPlugin implements Plugin<Project> {

    @Override
    public final void apply(Project project) {
        RecommendedProductDependenciesExtension ext = project.getExtensions()
                .create("recommendedProductDependencies", RecommendedProductDependenciesExtension.class, project);

        project.getPluginManager().withPlugin("java", _plugin -> {
            Provider<Directory> dir = project.getLayout().getBuildDirectory().dir("product-dependencies");
            TaskProvider<CompileRecommendedProductDependencies> compilePdeps = project.getTasks()
                    .register(
                            "compileRecommendedProductDependencies",
                            CompileRecommendedProductDependencies.class,
                            task -> {
                                task.getRecommendedProductDependencies()
                                        .set(ext.getRecommendedProductDependenciesProvider());
                                task.getOutputFile()
                                        .set(dir.map(directory -> directory.file(
                                                RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY
                                                        + "/product-dependencies.json")));
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
        });
    }
}
