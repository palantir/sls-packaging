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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.palantir.gradle.dist.ObjectMappers;
import com.palantir.gradle.dist.ProductDependency;
import com.palantir.gradle.dist.ProductDependencyMerger;
import com.palantir.gradle.dist.ProductId;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class ResolveProductDependenciesTask extends DefaultTask {
    private static final Logger log = Logging.getLogger(ResolveProductDependenciesTask.class);

    @Input
    abstract Property<String> getServiceName();

    @Input
    abstract Property<String> getServiceGroup();

    @Input
    abstract ListProperty<ProductDependency> getProductDependencies();

    @Input
    abstract SetProperty<ProductId> getInRepoProductIds();

    @Input
    abstract SetProperty<ProductId> getOptionalProductIds();

    @Input
    abstract SetProperty<ProductId> getIgnoredProductIds();

    @InputFiles
    public abstract ConfigurableFileCollection getProductDependenciesFiles();

    @OutputFile
    public abstract RegularFileProperty getManifestFile();

    @TaskAction
    public final void resolve() throws IOException {
        Map<ProductId, ProductDependency> allProductDependencies =
                computeDependencies(getProductDependencies().get(), discoverProductDependencies());

        ObjectMappers.writeProductDependencyManifest(
                ProductDependencyManifest.of(allProductDependencies.values().stream()
                        .sorted(Comparator.comparing(ProductDependency::getProductGroup)
                                .thenComparing(ProductDependency::getProductName))
                        .collect(ImmutableList.toImmutableList())),
                getManifestFile().getAsFile().get());
    }

    @VisibleForTesting
    final Map<ProductId, ProductDependency> computeDependencies(
            List<ProductDependency> declaredDependencies,
            Multimap<ProductId, ProductDependency> discoveredDependencies) {
        Map<ProductId, ProductDependency> allProductDependencies = new HashMap<>();
        Set<ProductId> allOptionalDependencies =
                new HashSet<>(getOptionalProductIds().get());

        declaredDependencies.forEach(declaredDep -> {
            ProductId productId = ProductId.of(declaredDep);
            Preconditions.checkArgument(
                    !getServiceGroup().get().equals(productId.getProductGroup())
                            || !getServiceName().get().equals(productId.getProductName()),
                    "Invalid for product to declare an explicit dependency on itself, please remove: %s",
                    declaredDep);

            if (getIgnoredProductIds().get().contains(productId)) {
                throw new IllegalArgumentException(String.format(
                        "Encountered product dependency declaration that was also ignored for '%s', either remove the "
                                + "dependency or ignore",
                        productId));
            }
            if (getOptionalProductIds().get().contains(productId)) {
                throw new IllegalArgumentException(String.format(
                        "Encountered product dependency declaration that was also declared as optional for '%s', "
                                + "either remove the dependency or optional declaration",
                        productId));
            }
            allProductDependencies.merge(
                    productId, declaredDep, (dep1, dep2) -> mergeDependencies(productId, dep1, dep2));
            if (declaredDep.getOptional()) {
                log.trace("Product dependency for '{}' declared as optional", productId);
                allOptionalDependencies.add(productId);
            }
        });

        // Perform initial merge of the discovered dependencies so that if there are overlaps with declared
        // dependencies we only log a single time.
        Map<ProductId, ProductDependency> mergedDiscoveredDependencies = new HashMap<>();
        discoveredDependencies.forEach((productId, discoveredDependency) -> {
            if (isSelfDependency(productId)) {
                return;
            }
            if (getIgnoredProductIds().get().contains(productId)) {
                log.trace("Ignored product dependency for '{}'", productId);
                return;
            }
            mergedDiscoveredDependencies.merge(
                    productId,
                    discoveredDependency,
                    (declaredDependency, _newDependency) ->
                            mergeDependencies(productId, declaredDependency, discoveredDependency));
        });

        mergedDiscoveredDependencies.forEach((productId, discoveredDependency) -> {
            allProductDependencies.merge(productId, discoveredDependency, (declaredDependency, _newDependency) -> {
                log.error(
                        "Please remove your declared product dependency on '{}' because it is"
                                + " already provided by a jar dependency:\n\n"
                                + "\tProvided:     {}\n"
                                + "\tYou declared: {}",
                        productId,
                        discoveredDependency,
                        declaredDependency);
                return mergeDependencies(productId, declaredDependency, discoveredDependency);
            });
        });

        allOptionalDependencies.stream()
                .map(productId -> Optional.ofNullable(allProductDependencies.get(productId))
                        .orElseThrow(() -> new IllegalStateException(String.format(
                                "Unable to mark missing product dependency '%s' as optional", productId))))
                .forEach(dep -> dep.setOptional(true));

        return allProductDependencies;
    }

    private Multimap<ProductId, ProductDependency> discoverProductDependencies() {
        return getProductDependenciesFiles().getFiles().stream()
                .map(ObjectMappers::readRecommendedProductDependencies)
                .flatMap(pdeps -> pdeps.recommendedProductDependencies().stream())
                .collect(ImmutableSetMultimap.toImmutableSetMultimap(ProductId::of, Function.identity()));
    }

    private ProductDependency mergeDependencies(ProductId productId, ProductDependency dep1, ProductDependency dep2) {
        ProductDependency mergedDep = ProductDependencyMerger.merge(dep1, dep2);
        if (getInRepoProductIds().get().contains(productId)
                && (dep1.getMinimumVersion().equals(getProjectVersion())
                        || dep2.getMinimumVersion().equals(getProjectVersion()))) {
            mergedDep.setMinimumVersion(getProjectVersion());
        }
        return mergedDep;
    }

    private String getProjectVersion() {
        return getProject().getVersion().toString();
    }

    private boolean isSelfDependency(ProductId productId) {
        return getServiceGroup().get().equals(productId.getProductGroup())
                && getServiceName().get().equals(productId.getProductName());
    }
}
