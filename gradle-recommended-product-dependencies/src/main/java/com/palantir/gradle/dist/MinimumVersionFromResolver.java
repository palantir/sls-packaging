/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.versions.VersionsLockExtension;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.provider.Provider;

/**
 * Resolves {@link ProductDependency#getMinimumVersionFrom()} coordinates via a per-project resolvable configuration.
 *
 * <p>Calling {@code com.palantir.gradle.versions}'s {@code getVersion(...)} inside a {@code productDependency} closure
 * is unsafe under Gradle 9: the closure runs at task-action time on a subproject, and {@code getVersion} resolves the
 * <em>root</em> project's {@code :unifiedClasspath} configuration without holding its exclusive lock. The
 * {@code minimumVersionFrom = 'group:name'} declaration lets the plugin do the lookup against a configuration owned
 * by the <em>same</em> project — so the lock requirement is met — and, when gradle-consistent-versions is applied,
 * GCV's lockfile-driven constraints supply the resolved version.
 */
public final class MinimumVersionFromResolver {

    private MinimumVersionFromResolver() {}

    /**
     * Registers a resolvable configuration named {@code configurationName} on {@code project}, populated with one
     * dependency per distinct {@link ProductDependency#getMinimumVersionFrom()} value found in {@code productDependencies}.
     * If gradle-consistent-versions is applied (to the root project), the configuration is added to the project's
     * {@link VersionsLockExtension} so lockfile-driven constraints apply.
     */
    public static NamedDomainObjectProvider<Configuration> registerVersionLookupConfiguration(
            Project project,
            String configurationName,
            Provider<? extends Collection<ProductDependency>> productDependencies) {
        Provider<List<Dependency>> versionLookupDeps = productDependencies.map(declared -> declared.stream()
                .flatMap(productDependency -> productDependency.getMinimumVersionFrom().stream())
                .distinct()
                .map(coordinate -> project.getDependencies().create(coordinate))
                .collect(Collectors.toList()));

        NamedDomainObjectProvider<Configuration> versionLookup = project.getConfigurations()
                .register(configurationName, configuration -> {
                    configuration.setCanBeResolved(true);
                    configuration.setCanBeConsumed(false);
                    configuration.setVisible(false);
                    configuration.setDescription(
                            "Resolves versions referenced by productDependency { minimumVersionFrom ... }.");
                    configuration.getDependencies().addAllLater(versionLookupDeps);
                });

        project.getRootProject().getPluginManager().withPlugin("com.palantir.consistent-versions", _appliedPlugin -> {
            VersionsLockExtension versionsLock = project.getExtensions().getByType(VersionsLockExtension.class);
            versionsLock.production(scopeConfigurer -> scopeConfigurer.from(configurationName));
        });

        return versionLookup;
    }

    /**
     * Returns a Provider that, when queried, yields {@code productDependencies} with any
     * {@link ProductDependency#getMinimumVersionFrom()} coordinate replaced by the version resolved from
     * {@code versionLookup}. Dependencies without {@code minimumVersionFrom} are returned unchanged.
     */
    public static Provider<List<ProductDependency>> resolveMinimumVersions(
            Provider<? extends Collection<ProductDependency>> productDependencies,
            NamedDomainObjectProvider<Configuration> versionLookup) {
        Provider<Map<String, String>> versionsByCoordinate = versionLookup.map(configuration ->
                configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                        .map(ResolvedComponentResult::getModuleVersion)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(
                                moduleVersion -> moduleVersion.getGroup() + ":" + moduleVersion.getName(),
                                ModuleVersionIdentifier::getVersion)));

        return productDependencies.zip(
                versionsByCoordinate,
                (declared, versionsForCoordinate) -> declared.stream()
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
                        : ProductDependency.generateMaxVersion(resolvedVersion),
                original.getRecommendedVersion(),
                original.getOptional());
    }
}
