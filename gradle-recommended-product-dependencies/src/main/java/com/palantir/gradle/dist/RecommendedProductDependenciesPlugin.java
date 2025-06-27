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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.Preconditions;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.java.archives.Manifest;
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
            validateProductDependencies(project, ext);
        });
    }

    private void validateProductDependencies(Project project, RecommendedProductDependenciesExtension ext) {
        TaskProvider<ValidateProductDependenciesTask> validateProductDependenciesTask = project.getTasks()
                .register(
                        "validateProductDependencies",
                        ValidateProductDependenciesTask.class,
                        task -> task.getRecommendedProductDependencies()
                                .set(ext.getRecommendedProductDependenciesProvider()));

        project.getTasks().named("check").configure(task -> task.dependsOn(validateProductDependenciesTask));
    }

    private void configureManifest(Project project, RecommendedProductDependenciesExtension ext) {
        // Capture the provider during configuration time to avoid serializing non-serializable objects
        final Provider<?> productDependenciesProvider = ext.getRecommendedProductDependenciesProvider();

        // Directly configure the Jar task's manifest using doFirst
        project.getTasks().withType(Jar.class).named(JavaPlugin.JAR_TASK_NAME).configure(jar -> {
            jar.doFirst(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    Jar jarTask = (Jar) task;
                    Preconditions.checkState(
                            !jarTask.getState().getExecuted(), "Attempted to configure jar task after it was executed");
                    jarTask.manifest(new Action<Manifest>() {
                        @Override
                        public void execute(Manifest manifest) {
                            String recommendedProductDeps;
                            try {
                                @SuppressWarnings("unchecked")
                                Set<ProductDependency> dependencies =
                                        (Set<ProductDependency>) productDependenciesProvider.get();
                                recommendedProductDeps = new ObjectMapper()
                                        .writeValueAsString(RecommendedProductDependencies.builder()
                                                .recommendedProductDependencies(dependencies)
                                                .build());
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(
                                        "Couldn't serialize recommended product dependencies as string", e);
                            }
                            manifest.attributes(ImmutableMap.of(
                                    RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY,
                                    recommendedProductDeps));
                        }
                    });
                }
            });
        });
    }

    private void embedResource(Project project, RecommendedProductDependenciesExtension ext) {
        TaskProvider<CompileRecommendedProductDependencies> compilePdeps = project.getTasks()
                .register(
                        "compileRecommendedProductDependencies", CompileRecommendedProductDependencies.class, task -> {
                            task.getRecommendedProductDependencies()
                                    .set(ext.getRecommendedProductDependenciesProvider());
                            task.getOutputDir()
                                    .set(project.getLayout().getBuildDirectory().dir("product-dependencies"));
                        });

        project.getTasks()
                .named(
                        JavaPlugin.PROCESS_RESOURCES_TASK_NAME,
                        processResources -> processResources.dependsOn(compilePdeps));

        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        sourceSets.named("main").configure(sourceSet -> {
            sourceSet.getResources().srcDir(compilePdeps.map(CompileRecommendedProductDependencies::getOutputDir));
        });
    }
}
