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
import com.palantir.gradle.dist.ProductDependency;
import com.palantir.gradle.dist.ProductDependencyIntrospectionPlugin;
import com.palantir.gradle.dist.RecommendedProductDependencies;
import com.palantir.gradle.dist.RecommendedProductDependenciesPlugin;
import com.palantir.gradle.dist.artifacts.DependencyDiscovery;
import com.palantir.gradle.dist.artifacts.ExtractSingleFileOrManifest;
import com.palantir.gradle.dist.artifacts.PreferProjectCompatibilityRule;
import com.palantir.gradle.dist.artifacts.SelectSingleFile;
import com.palantir.gradle.versions.VersionsLockExtension;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

public final class ProductDependencies {

    private static final String PRODUCT_DEPENDENCIES = "product-dependencies";
    public static final String PRODUCT_DEPENDENCY_DISCOVERY_CONFIGURATION_NAME = "productDependencyDiscovery";
    public static final String PRODUCT_DEPENDENCY_VERSION_LOOKUP_CONFIGURATION_NAME = "productDependencyVersionLookup";

    public static TaskProvider<ResolveProductDependenciesTask> registerProductDependencyTasks(
            Project project, BaseDistributionExtension ext) {
        Provider<Directory> pdepsDir = project.getLayout().getBuildDirectory().dir("resolved-pdeps");

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

        NamedDomainObjectProvider<Configuration> versionLookup = registerVersionLookupConfiguration(project, ext);

        return project.getTasks().register("resolveProductDependencies", ResolveProductDependenciesTask.class, task -> {
            task.getServiceName().set(ext.getDistributionServiceName());
            task.getServiceGroup().set(ext.getDistributionServiceGroup());

            task.getInRepoProductIds()
                    .set(project.provider(
                            () -> ProductDependencyIntrospectionPlugin.getInRepoProductIds(project.getRootProject())
                                    .keySet()));
            task.getProductDependencies().set(resolveMinimumVersionsFrom(ext, versionLookup));
            task.getOptionalProductIds().set(ext.getOptionalProductDependencies());
            task.getIgnoredProductIds().set(ext.getIgnoredProductDependencies());

            task.getProductDependenciesFiles()
                    .from(discoveredDependencies.map(
                            pdeps -> pdeps.getArtifacts().getArtifactFiles()));

            task.getManifestFile().set(pdepsDir.map(dir -> dir.file("pdeps-manifest.json")));
        });
    }

    private static Provider<List<ProductDependency>> resolveMinimumVersionsFrom(
            BaseDistributionExtension ext, NamedDomainObjectProvider<Configuration> versionLookup) {
        Provider<Map<String, String>> versionsByCoordinate = versionLookup.map(configuration ->
                configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                        .map(ResolvedComponentResult::getModuleVersion)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(
                                moduleVersion -> moduleVersion.getGroup() + ":" + moduleVersion.getName(),
                                ModuleVersionIdentifier::getVersion)));

        return ext.getAllProductDependencies()
                .zip(
                        versionsByCoordinate,
                        (productDependencies, versionsForCoordinate) -> productDependencies.stream()
                                .map(productDependency ->
                                        withResolvedMinimumVersion(productDependency, versionsForCoordinate))
                                .toList());
    }

    private static ProductDependency withResolvedMinimumVersion(
            ProductDependency original, Map<String, String> versionsByCoordinate) {
        Optional<String> coordinate = original.getMinimumVersionFrom();
        if (coordinate.isEmpty()) {
            return original;
        }
        String resolvedVersion = Optional.ofNullable(versionsByCoordinate.get(coordinate.get()))
                .orElseThrow(() -> new GradleException(String.format(
                        "Unable to resolve minimumVersionFrom '%s' for product dependency %s:%s",
                        coordinate.get(), original.getProductGroup(), original.getProductName())));
        return new ProductDependency(
                original.getProductGroup(),
                original.getProductName(),
                resolvedVersion,
                original.getMaximumVersion() != null
                        ? original.getMaximumVersion()
                        : BaseDistributionExtension.generateMaxVersion(resolvedVersion),
                original.getRecommendedVersion(),
                original.getOptional());
    }

    /**
     * Registers a resolvable configuration that the plugin populates with the {@code group:name} entries declared via
     * {@link ProductDependency#getMinimumVersionFrom()}. When gradle-consistent-versions is applied the configuration
     * is also added to the {@code versionsLock} extension so that lockfile-driven constraints apply when it is
     * resolved.
     */
    private static NamedDomainObjectProvider<Configuration> registerVersionLookupConfiguration(
            Project project, BaseDistributionExtension ext) {
        Provider<List<Dependency>> versionLookupDeps = ext.getAllProductDependencies()
                .map(productDependencies -> productDependencies.stream()
                        .flatMap(productDependency -> productDependency.getMinimumVersionFrom().stream())
                        .distinct()
                        .map(coordinate -> project.getDependencies().create(coordinate))
                        .collect(Collectors.toList()));

        NamedDomainObjectProvider<Configuration> versionLookup = project.getConfigurations()
                .register(PRODUCT_DEPENDENCY_VERSION_LOOKUP_CONFIGURATION_NAME, configuration -> {
                    configuration.setCanBeResolved(true);
                    configuration.setCanBeConsumed(false);
                    configuration.getDependencies().addAllLater(versionLookupDeps);
                });

        project.getRootProject().getPluginManager().withPlugin("com.palantir.consistent-versions", _appliedPlugin -> {
            VersionsLockExtension versionsLock = project.getExtensions().getByType(VersionsLockExtension.class);
            versionsLock.production(
                    scopeConfigurer -> scopeConfigurer.from(PRODUCT_DEPENDENCY_VERSION_LOOKUP_CONFIGURATION_NAME));
        });

        return versionLookup;
    }

    private ProductDependencies() {}
}
