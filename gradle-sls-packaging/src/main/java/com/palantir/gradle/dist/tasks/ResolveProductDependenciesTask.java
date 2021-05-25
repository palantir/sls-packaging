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

package com.palantir.gradle.dist.tasks;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.palantir.gradle.dist.BaseDistributionExtension;
import com.palantir.gradle.dist.ConfigureProductDependenciesTask;
import com.palantir.gradle.dist.ProductDependency;
import com.palantir.gradle.dist.ProductDependencyMerger;
import com.palantir.gradle.dist.ProductDependencyReport;
import com.palantir.gradle.dist.ProductId;
import com.palantir.gradle.dist.RecommendedProductDependencies;
import com.palantir.gradle.dist.RecommendedProductDependenciesPlugin;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

@CacheableTask
public class ResolveProductDependenciesTask extends DefaultTask {
    private static final Logger log = Logging.getLogger(ResolveProductDependenciesTask.class);

    private final SetProperty<ProductId> inRepoProductIds =
            getProject().getObjects().setProperty(ProductId.class);
    private final Property<String> serviceName = getProject().getObjects().property(String.class);
    private final Property<String> serviceGroup = getProject().getObjects().property(String.class);

    private final ListProperty<ProductDependency> declaredProductDependencies =
            getProject().getObjects().listProperty(ProductDependency.class);
    private final SetProperty<ProductId> optionalProductIds =
            getProject().getObjects().setProperty(ProductId.class);
    private final SetProperty<ProductId> ignoredProductIds =
            getProject().getObjects().setProperty(ProductId.class);
    private final Property<Configuration> productDependenciesConfig =
            getProject().getObjects().property(Configuration.class);
    private final ListProperty<ProductDependency> discoveredProductDependencies =
            getProject().getObjects().listProperty(ProductDependency.class);

    private final RegularFileProperty outputFile = getProject().getObjects().fileProperty();

    public ResolveProductDependenciesTask() {
        dependsOn(otherProjectProductDependenciesTasks());
        outputFile.convention(() -> new File(getTemporaryDir(), "resolved-product-dependencies.json"));
        discoveredProductDependencies.convention(getProject().provider(this::findRecommendedProductDependenies));
    }

    static TaskProvider<ResolveProductDependenciesTask> createResolveProductDependenciesTask(
            Project project, BaseDistributionExtension ext, Provider<Set<ProductId>> provider) {
        TaskProvider<ResolveProductDependenciesTask> depTask = project.getTasks()
                .register("resolveProductDependencies", ResolveProductDependenciesTask.class, task -> {
                    task.getServiceName().set(ext.getDistributionServiceName());
                    task.getServiceGroup().set(ext.getDistributionServiceGroup());
                    task.getDeclaredProductDependencies().set(ext.getAllProductDependencies());
                    task.setConfiguration(project.provider(ext::getProductDependenciesConfig));
                    task.getOptionalProductIds().set(ext.getOptionalProductDependencies());
                    task.getIgnoredProductIds().set(ext.getIgnoredProductDependencies());
                    task.getInRepoProductIds().set(provider);
                });
        return depTask;
    }

    /**
     * A lazy collection of tasks that ensure the {@link Jar} task of any project dependencies from
     * {@link #productDependenciesConfig} is correctly populated with the recommended product dependencies of that
     * project, if any (specifically, if they apply the {@link RecommendedProductDependenciesPlugin}).
     */
    private Provider<FileCollection> otherProjectProductDependenciesTasks() {
        return productDependenciesConfig.map(productDeps -> {
            // Using a ConfigurableFileCollection simply because it implements Buildable and provides a convenient API
            // to wire up task dependencies to it in a lazy way.
            ConfigurableFileCollection emptyFileCollection = getProject().files();
            productDeps.getIncoming().getArtifacts().getArtifacts().stream()
                    .flatMap(artifact -> {
                        ComponentIdentifier id = artifact.getId().getComponentIdentifier();

                        // Depend on the ConfigureProductDependenciesTask, if it exists, which will wire up the jar
                        // manifest
                        // with recommended product dependencies.
                        if (id instanceof ProjectComponentIdentifier) {
                            Project dependencyProject = getProject()
                                    .getRootProject()
                                    .project(((ProjectComponentIdentifier) id).getProjectPath());
                            return Stream.of(
                                    dependencyProject.getTasks().withType(ConfigureProductDependenciesTask.class));
                        }
                        return Stream.empty();
                    })
                    .forEach(emptyFileCollection::builtBy);
            return emptyFileCollection;
        });
    }

    @Input
    final Property<String> getServiceName() {
        return serviceName;
    }

    @Input
    final Property<String> getServiceGroup() {
        return serviceGroup;
    }

    @Input
    final ListProperty<ProductDependency> getDeclaredProductDependencies() {
        return declaredProductDependencies;
    }

    @Input
    final ListProperty<ProductDependency> getDiscoveredProductDependencies() {
        return discoveredProductDependencies;
    }

    @Input
    final SetProperty<ProductId> getOptionalProductIds() {
        return optionalProductIds;
    }

    @Input
    final SetProperty<ProductId> getIgnoredProductIds() {
        return ignoredProductIds;
    }

    @Input
    final SetProperty<ProductId> getInRepoProductIds() {
        return inRepoProductIds;
    }

    /**
     * Contents of the given configuration.  Cannot list the configuration itself as an input property because the
     * caching calculations attempt to resolve the configuration at config time.  This can lead to an error for
     * configs that connot be resolved directly at that stage.  Caching and up-to-date calcs thus use this property.
     */
    @Input
    final Set<String> getProductDependenciesConfig() {
        return productDependenciesConfig.get().getIncoming().getResolutionResult().getAllComponents().stream()
                .map(ResolvedComponentResult::getId)
                .map(ComponentIdentifier::getDisplayName)
                .collect(Collectors.toSet());
    }

    final void setConfiguration(Provider<Configuration> config) {
        this.productDependenciesConfig.set(config);
    }

    @OutputFile
    final RegularFileProperty getOutputFile() {
        return outputFile;
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    @TaskAction
    final void computeProductDependencies() throws IOException {
        Map<ProductId, ProductDependency> allProductDependencies = new HashMap<>();
        Set<ProductId> allOptionalDependencies =
                new HashSet<>(getOptionalProductIds().get());
        getDeclaredProductDependencies().get().forEach(declaredDep -> {
            ProductId productId = ProductId.of(declaredDep);
            Preconditions.checkArgument(
                    !serviceGroup.get().equals(productId.getProductGroup())
                            || !serviceName.get().equals(productId.getProductName()),
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
            allProductDependencies.merge(productId, declaredDep, ProductDependencyMerger::merge);
            if (declaredDep.getOptional()) {
                log.trace("Product dependency for '{}' declared as optional", productId);
                allOptionalDependencies.add(productId);
            }
        });

        // Merge all discovered and declared product dependencies
        dedupDiscoveredProductDependencies().forEach(discoveredDependency -> {
            ProductId productId = ProductId.of(discoveredDependency);
            if (getIgnoredProductIds().get().contains(productId)) {
                log.trace("Ignored product dependency for '{}'", productId);
                return;
            }
            allProductDependencies.merge(productId, discoveredDependency, (declaredDependency, _newDependency) -> {
                ProductDependency mergedDependency =
                        ProductDependencyMerger.merge(declaredDependency, discoveredDependency);
                if (mergedDependency.equals(discoveredDependency)) {
                    getLogger()
                            .error(
                                    "Please remove your declared product dependency on '{}' because it is"
                                            + " already provided by a jar dependency:\n\n"
                                            + "\tProvided:     {}\n"
                                            + "\tYou declared: {}",
                                    productId,
                                    discoveredDependency,
                                    declaredDependency);
                }
                return mergedDependency;
            });
        });

        // Ensure optional product dependencies are marked as such.
        allOptionalDependencies.forEach(
                productId -> allProductDependencies.computeIfPresent(productId, (_productId, existingDep) -> {
                    log.trace("Product dependency for '{}' set as optional", productId);
                    existingDep.setOptional(true);
                    return existingDep;
                }));

        List<ProductDependency> productDeps = allProductDependencies.values().stream()
                .sorted(Comparator.comparing(ProductDependency::getProductGroup)
                        .thenComparing(ProductDependency::getProductName))
                .collect(ImmutableList.toImmutableList());

        CreateManifestTask.jsonMapper.writeValue(
                getOutputFile().getAsFile().get(), ProductDependencyReport.of(productDeps));
    }

    private Set<ProductDependency> dedupDiscoveredProductDependencies() {
        // De-dup the set of discovered dependencies so that if one is a dupe of the manually set dependencies, we only
        // display the "please remove the manual setting" message once.
        Map<ProductId, ProductDependency> discoveredDeps = new HashMap<>();
        discoveredProductDependencies.get().forEach(productDependency -> {
            ProductId productId = ProductId.of(productDependency);
            discoveredDeps.merge(productId, productDependency, ProductDependencyMerger::merge);
        });
        return ImmutableSet.copyOf(discoveredDeps.values());
    }

    private List<ProductDependency> findRecommendedProductDependenies() {
        return productDependenciesConfig.get().getIncoming().getArtifacts().getArtifacts().stream()
                .flatMap(artifact -> {
                    String artifactName = artifact.getId().getDisplayName();
                    ComponentIdentifier id = artifact.getId().getComponentIdentifier();
                    Optional<String> pdeps = Optional.empty();

                    // Extract product dependencies directly from Jar task for in project dependencies
                    if (id instanceof ProjectComponentIdentifier) {
                        Project dependencyProject = getProject()
                                .getRootProject()
                                .project(((ProjectComponentIdentifier) id).getProjectPath());
                        if (dependencyProject.getPlugins().hasPlugin(JavaPlugin.class)) {
                            Jar jar = (Jar) dependencyProject.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);

                            pdeps = Optional.ofNullable(jar.getManifest()
                                            .getEffectiveManifest()
                                            .getAttributes()
                                            .get(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY))
                                    .map(Object::toString);
                        }
                    } else {
                        if (!artifact.getFile().exists()) {
                            log.debug("Artifact did not exist: {}", artifact.getFile());
                            return Stream.empty();
                        } else if (!com.google.common.io.Files.getFileExtension(
                                        artifact.getFile().getName())
                                .equals("jar")) {
                            log.debug("Artifact is not jar: {}", artifact.getFile());
                            return Stream.empty();
                        }

                        Manifest manifest;
                        try {
                            ZipFile zipFile = new ZipFile(artifact.getFile());
                            ZipEntry manifestEntry = zipFile.getEntry("META-INF/MANIFEST.MF");
                            if (manifestEntry == null) {
                                log.debug("Manifest file does not exist in jar for '${coord}'");
                                return Stream.empty();
                            }
                            manifest = new Manifest(zipFile.getInputStream(manifestEntry));
                        } catch (IOException e) {
                            log.warn(
                                    "IOException encountered when processing artifact '{}', file '{}', {}",
                                    artifactName,
                                    artifact.getFile(),
                                    e);
                            return Stream.empty();
                        }

                        pdeps = Optional.ofNullable(manifest.getMainAttributes()
                                .getValue(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY));
                    }
                    if (!pdeps.isPresent()) {
                        log.debug(
                                "No product dependency found for artifact '{}', file '{}'",
                                artifactName,
                                artifact.getFile());
                        return Stream.empty();
                    }

                    try {
                        RecommendedProductDependencies recommendedDeps = CreateManifestTask.jsonMapper.readValue(
                                pdeps.get(), RecommendedProductDependencies.class);
                        return recommendedDeps.recommendedProductDependencies().stream()
                                .peek(productDependency -> log.info(
                                        "Product dependency recommendation made by artifact '{}', file '{}', "
                                                + "dependency recommendation '{}'",
                                        artifactName,
                                        artifact,
                                        productDependency))
                                .map(recommendedDep -> new ProductDependency(
                                        recommendedDep.getProductGroup(),
                                        recommendedDep.getProductName(),
                                        recommendedDep.getMinimumVersion(),
                                        recommendedDep.getMaximumVersion(),
                                        recommendedDep.getRecommendedVersion(),
                                        recommendedDep.getOptional()));
                    } catch (IOException | IllegalArgumentException e) {
                        log.debug(
                                "Failed to load product dependency for artifact '{}', file '{}', '{}'",
                                artifactName,
                                artifact,
                                e);
                        return Stream.empty();
                    }
                })
                .filter(this::isNotSelfProductDependency)
                .collect(Collectors.toList());
    }

    private boolean isNotSelfProductDependency(ProductDependency dependency) {
        return !serviceGroup.get().equals(dependency.getProductGroup())
                || !serviceName.get().equals(dependency.getProductName());
    }
}
