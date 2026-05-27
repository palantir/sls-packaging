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

package com.palantir.gradle.dist.pdeps;

import com.palantir.gradle.dist.BaseDistributionExtension;
import com.palantir.gradle.dist.ProductDependencyIntrospectionPlugin;
import com.palantir.gradle.dist.RecommendedProductDependencies;
import com.palantir.gradle.dist.RecommendedProductDependenciesPlugin;
import com.palantir.gradle.dist.artifacts.DependencyDiscovery;
import com.palantir.gradle.dist.artifacts.ExtractSingleFileOrManifest;
import com.palantir.gradle.dist.artifacts.PreferProjectCompatibilityRule;
import com.palantir.gradle.dist.artifacts.SelectSingleFile;
import java.util.Map;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

public final class ProductDependencies {

    private static final String PRODUCT_DEPENDENCIES = "product-dependencies";
    public static final String PRODUCT_DEPENDENCY_DISCOVERY_CONFIGURATION_NAME = "productDependencyDiscovery";

    public static TaskProvider<ResolveProductDependenciesTask> registerProductDependencyTasks(
            Project project, BaseDistributionExtension ext) {
        // Register compatibility rule to ensure that ResourceTransform is applied onto project dependencies so we
        // avoid compilation
        PreferProjectCompatibilityRule.configureRule(project);

        DependencyDiscovery.configureJarTransform(
                project, ExtractSingleFileOrManifest.class, PRODUCT_DEPENDENCIES, params -> {
                    params.getPathToExtract().set(RecommendedProductDependenciesPlugin.RESOURCE_PATH);
                    params.getKeyToExtract().set(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY);
                });

        DependencyDiscovery.configureResourceTransform(
                project, SelectSingleFile.class, PRODUCT_DEPENDENCIES, params -> {
                    params.getPathToExtract().set(RecommendedProductDependenciesPlugin.RESOURCE_PATH);
                });

        NamedDomainObjectProvider<Configuration> productDependencyDependencyScope = project.getConfigurations()
                .register(PRODUCT_DEPENDENCY_DISCOVERY_CONFIGURATION_NAME, conf -> {
                    conf.setCanBeResolved(false);
                    conf.setCanBeConsumed(false);
                });
        NamedDomainObjectProvider<Configuration> productDependencyClasspath = project.getConfigurations()
                .register("productDependencyClasspath", conf -> {
                    conf.setCanBeResolved(true);
                    conf.setCanBeConsumed(false);
                    conf.extendsFrom(productDependencyDependencyScope.get());
                });

        Provider<Dependency> consumableProjectDependency = ext.getConsumableProductDependenciesConfigName()
                .map(configurationName -> {
                    Map<String, String> projectDependency =
                            Map.of("path", project.getPath(), "configuration", configurationName);
                    return project.getDependencies().project(projectDependency);
                });
        project.getDependencies().addProvider(productDependencyDependencyScope.getName(), consumableProjectDependency);

        Provider<ArtifactView> discoveredDependencies = productDependencyClasspath.map(
                conf -> DependencyDiscovery.getFilteredArtifact(project, conf, PRODUCT_DEPENDENCIES));

        TaskProvider<ResolveProductDependenciesTask> rootWorker =
                registerRootWorker(project, ext, discoveredDependencies);

        // The subproject keeps the public task name; it just delegates to the root worker.
        boolean ownsTaskName = project.equals(project.getRootProject());
        if (!ownsTaskName) {
            project.getTasks().register("resolveProductDependencies", task -> {
                task.dependsOn(rootWorker);
            });
        }

        return rootWorker;
    }

    private static TaskProvider<ResolveProductDependenciesTask> registerRootWorker(
            Project project, BaseDistributionExtension ext, Provider<ArtifactView> discoveredDependencies) {
        Project rootProject = project.getRootProject();
        String taskName = rootWorkerTaskName(project);
        Provider<Directory> pdepsDir =
                rootProject.getLayout().getBuildDirectory().dir(rootWorkerOutputDir(project));

        return rootProject.getTasks().register(taskName, ResolveProductDependenciesTask.class, task -> {
            task.getServiceName().set(ext.getDistributionServiceName());
            task.getServiceGroup().set(ext.getDistributionServiceGroup());

            task.getInRepoProductIds()
                    .set(project.provider(() -> ProductDependencyIntrospectionPlugin.getInRepoProductIds(rootProject)
                            .keySet()));
            task.getProductDependencies().set(ext.getAllProductDependencies());
            task.getOptionalProductIds().set(ext.getOptionalProductDependencies());
            task.getIgnoredProductIds().set(ext.getIgnoredProductDependencies());

            task.getProductDependenciesFiles()
                    .from(discoveredDependencies.map(
                            pdeps -> pdeps.getArtifacts().getArtifactFiles()));

            task.getManifestFile().set(pdepsDir.map(dir -> dir.file("pdeps-manifest.json")));
        });
    }

    private static String rootWorkerTaskName(Project project) {
        if (project.equals(project.getRootProject())) {
            return "resolveProductDependencies";
        }
        return "resolveProductDependenciesFor" + project.getPath().replace(':', '_');
    }

    private static String rootWorkerOutputDir(Project project) {
        if (project.equals(project.getRootProject())) {
            return "resolved-pdeps";
        }
        return "resolved-pdeps" + project.getPath().replace(':', '/');
    }

    private ProductDependencies() {}
}
