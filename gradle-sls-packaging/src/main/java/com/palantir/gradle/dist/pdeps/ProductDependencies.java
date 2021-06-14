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
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

public final class ProductDependencies {

    public static TaskProvider<ResolveProductDependenciesTask> registerProductDependencyTasks(
            Project project, String sourceConfiguration, BaseDistributionExtension ext) {
        Provider<Directory> pdepsDir = project.getLayout().getBuildDirectory().dir("product-dependencies");
        Configuration pdepsConfig =
                DependencyDiscovery.copyConfiguration(project, sourceConfiguration, "productDependencies");

        // Register compatibility rule to ensure that ResourceTransform is applied onto project dependencies so we
        // avoid compilation
        PreferProjectCompatibilityRule.configureRule(project);

        DependencyDiscovery.configureJarTransform(
                project, ExtractSingleFileOrManifest.class, DependencyDiscovery.PRODUCT_DEPENDENCIES, params -> {
                    params.getPathToExtract().set(RecommendedProductDependenciesPlugin.RESOURCE_PATH);
                    params.getKeyToExtract().set(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY);
                });

        DependencyDiscovery.configureResourceTransform(
                project, SelectSingleFile.class, DependencyDiscovery.PRODUCT_DEPENDENCIES, params -> {
                    params.getPathToExtract().set(RecommendedProductDependenciesPlugin.RESOURCE_PATH);
                });

        ArtifactView discoveredDependencies =
                DependencyDiscovery.getFilteredArtifact(project, pdepsConfig, DependencyDiscovery.PRODUCT_DEPENDENCIES);

        return project.getTasks().register("resolveProductDependencies", ResolveProductDependenciesTask.class, task -> {
            task.getServiceName().set(ext.getDistributionServiceName());
            task.getServiceGroup().set(ext.getDistributionServiceGroup());

            task.getInRepoProductIds()
                    .set(project.provider(
                            () -> ProductDependencyIntrospectionPlugin.getInRepoProductIds(project.getRootProject())
                                    .keySet()));
            task.getProductDependencies().set(ext.getAllProductDependencies());
            task.getOptionalProductIds().set(ext.getOptionalProductDependencies());
            task.getIgnoredProductIds().set(ext.getIgnoredProductDependencies());

            task.getProductDependenciesFiles()
                    .from(discoveredDependencies.getArtifacts().getArtifactFiles());

            task.getManifestFile().set(pdepsDir.map(dir -> dir.file("pdeps-manifest.json")));
        });
    }

    private ProductDependencies() {}
}
