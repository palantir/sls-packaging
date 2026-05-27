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

import java.util.List;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

public class RecommendedProductDependenciesPlugin implements Plugin<Project> {

    public static final String RESOURCE_PATH =
            RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY + "/product-dependencies.json";

    public static final String RECOMMENDED_PRODUCT_DEPENDENCY_VERSION_LOOKUP_CONFIGURATION_NAME =
            "recommendedProductDependencyVersionLookup";

    @Override
    public final void apply(Project project) {
        @SuppressWarnings({"for-rollout:GradleTypesAsFields", "for-rollout:NonAbstractGradleType"})
        RecommendedProductDependenciesExtension ext = project.getExtensions()
                .create("recommendedProductDependencies", RecommendedProductDependenciesExtension.class, project);

        project.getPluginManager().withPlugin("java", _plugin -> {
            NamedDomainObjectProvider<Configuration> versionLookup =
                    MinimumVersionFromResolver.registerVersionLookupConfiguration(
                            project,
                            RECOMMENDED_PRODUCT_DEPENDENCY_VERSION_LOOKUP_CONFIGURATION_NAME,
                            ext.getRecommendedProductDependenciesProvider());
            Provider<List<ProductDependency>> resolvedDependencies = MinimumVersionFromResolver.resolveMinimumVersions(
                    ext.getRecommendedProductDependenciesProvider(), versionLookup);
            embedResource(project, resolvedDependencies);
            configureManifest(project, resolvedDependencies);
        });
    }

    @SuppressWarnings("for-rollout:TaskDependsOn")
    private void configureManifest(Project project, Provider<List<ProductDependency>> resolvedDependencies) {
        TaskProvider<ConfigureProductDependenciesTask> configureProductDependenciesTask = project.getTasks()
                .register("configureProductDependencies", ConfigureProductDependenciesTask.class, cmt -> {
                    cmt.setProductDependencies(resolvedDependencies);
                });

        // Ensure that the jar task depends on this wiring task
        project.getTasks().withType(Jar.class).named(JavaPlugin.JAR_TASK_NAME).configure(jar -> {
            jar.dependsOn(configureProductDependenciesTask);
        });
    }

    private void embedResource(Project project, Provider<List<ProductDependency>> resolvedDependencies) {
        TaskProvider<CompileRecommendedProductDependencies> compilePdeps = project.getTasks()
                .register(
                        "compileRecommendedProductDependencies", CompileRecommendedProductDependencies.class, task -> {
                            task.getRecommendedProductDependencies().set(resolvedDependencies);
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
