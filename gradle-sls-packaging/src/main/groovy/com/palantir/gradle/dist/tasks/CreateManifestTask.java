/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.palantir.gradle.dist.BaseDistributionExtension;
import com.palantir.gradle.dist.ProductDependency;
import com.palantir.gradle.dist.ProductId;
import com.palantir.gradle.dist.ProductType;
import com.palantir.gradle.dist.RecommendedProductDependencies;
import com.palantir.gradle.dist.RecommendedProductDependency;
import com.palantir.gradle.dist.RecommendedProductDependencyMerger;
import com.palantir.gradle.dist.SlsManifest;
import com.palantir.slspackaging.versions.SlsProductVersions;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateManifestTask extends DefaultTask {
    private static final Logger log = LoggerFactory.getLogger(CreateManifestTask.class);
    public static final String SLS_RECOMMENDED_PRODUCT_DEPS_KEY = "Sls-Recommended-Product-Dependencies";
    public static final ObjectMapper jsonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);

    private final Property<String> serviceName = getProject().getObjects().property(String.class);
    private final Property<String> serviceGroup = getProject().getObjects().property(String.class);
    private final Property<ProductType> productType = getProject().getObjects().property(ProductType.class);

    private final ListProperty<ProductDependency> productDependencies = getProject().getObjects()
            .listProperty(ProductDependency.class);
    private final SetProperty<ProductId> ignoredProductIds = getProject().getObjects().setProperty(ProductId.class);

    // TODO(forozco): Use MapProperty, RegularFileProperty once our minimum supported version is 5.1
    private Map<String, Object> manifestExtensions = Maps.newHashMap();
    private File manifestFile;

    private Configuration productDependenciesConfig;

    @Input
    public final Property<String> getServiceName() {
        return serviceName;
    }

    @Input
    public final Property<String> getServiceGroup() {
        return serviceGroup;
    }

    @Input
    public final Property<ProductType> getProductType() {
        return productType;
    }

    @Input
    public final Map<String, Object> getManifestExtensions() {
        return manifestExtensions;
    }

    public final void setManifestExtensions(Map<String, Object> manifestExtensions) {
        this.manifestExtensions = manifestExtensions;
    }

    @Input
    public final ListProperty<ProductDependency> getProductDependencies() {
        return productDependencies;
    }

    @Input
    public final SetProperty<ProductId> getIgnoredProductIds() {
        return ignoredProductIds;
    }

    @InputFiles
    public final FileCollection getProductDependenciesConfig() {
        return productDependenciesConfig;
    }

    public final void setProductDependenciesConfig(Configuration productDependenciesConfig) {
        this.productDependenciesConfig = productDependenciesConfig;
    }

    @Input
    public final String getProjectVersion() {
        return getProject().getVersion().toString();
    }

    @OutputFile
    public final File getManifestFile() {
        return manifestFile;
    }

    public final void setManifestFile(File manifestFile) {
        this.manifestFile = manifestFile;
    }

    @TaskAction
    @SuppressWarnings("checkstyle:cyclomaticComplexity")
    final void createManifest() throws IOException {
        validateProjectVersion();
        Map<String, Set<RecommendedProductDependency>> allRecommendedDepsByCoord = Maps.newHashMap();
        Map<String, String> mavenCoordsByProductIds = Maps.newHashMap();
        Map<String, RecommendedProductDependency> recommendedDepsByProductId = Maps.newHashMap();

        for (ResolvedArtifact artifact : productDependenciesConfig.getResolvedConfiguration().getResolvedArtifacts()) {
            String coord = identifierToCoord(artifact.getModuleVersion().getId());

            Manifest manifest;
            try {
                ZipFile zf = new ZipFile(artifact.getFile());
                ZipEntry manifestEntry = zf.getEntry("META-INF/MANIFEST.MF");
                if (manifestEntry == null) {
                    log.debug("Manifest file does not exist in jar for '{}'", coord);
                    continue;
                }
                manifest = new Manifest(zf.getInputStream(manifestEntry));
            } catch (IOException e) {
                log.warn("IOException encountered when processing artifact '{}', file '{}'",
                        coord, artifact.getFile(), e);
                continue;
            }

            String pdeps = manifest.getMainAttributes().getValue(SLS_RECOMMENDED_PRODUCT_DEPS_KEY);
            if (pdeps == null) {
                log.debug("No pdeps found in manifest for artifact '{}', file '{}'", coord, artifact.getFile());
                continue;
            }

            RecommendedProductDependencies recommendedDeps = jsonMapper.readValue(
                    pdeps, RecommendedProductDependencies.class);

            allRecommendedDepsByCoord.getOrDefault(coord, new HashSet<>())
                    .addAll(recommendedDeps.recommendedProductDependencies());

            recommendedDeps.recommendedProductDependencies().forEach(recommendedDep -> {
                String productId = String.format("%s:%s",
                        recommendedDep.getProductGroup(), recommendedDep.getProductName());
                RecommendedProductDependency existingDep = recommendedDepsByProductId.get(productId);
                RecommendedProductDependency dep = recommendedDep;
                if (mavenCoordsByProductIds.containsKey(productId)
                        && !Objects.equals(existingDep, recommendedDep)) {
                    String othercoord = mavenCoordsByProductIds.get(productId);
                    // Try to merge
                    log.info(
                            "Trying to merge duplicate product dependencies found for {} in '{}' and '{}': {} and {}",
                            productId,
                            coord,
                            othercoord,
                            recommendedDep,
                            existingDep);
                    dep = RecommendedProductDependencyMerger.merge(recommendedDep, existingDep);
                }

                mavenCoordsByProductIds.put(productId, coord);
                recommendedDepsByProductId.put(productId, dep);
            });
        }

        Set<String> seenRecommendedProductIds = Sets.newHashSet();
        List<ProductDependency> dependencies = productDependencies.get().stream().map(productDependency -> {
            String productId = String.format(
                    "%s:%s", productDependency.getProductGroup(), productDependency.getProductName());
            seenRecommendedProductIds.add(productId);

            if (!productDependency.getDetectConstraints()) {
                return productDependency;
            } else {
                if (!recommendedDepsByProductId.containsKey(productId)) {
                    throw new GradleException(String.format("Product dependency '%s' has constraint detection enabled, "
                                    + "but could not find any recommendations in %s configuration",
                            productId, productDependenciesConfig.getName()));
                }
                RecommendedProductDependency recommendedProductDependency = recommendedDepsByProductId.get(productId);
                try {
                    return new ProductDependency(
                            productDependency.getProductGroup(),
                            productDependency.getProductName(),
                            recommendedProductDependency.getMinimumVersion(),
                            recommendedProductDependency.getMaximumVersion(),
                            recommendedProductDependency.getRecommendedVersion());

                } catch (IllegalArgumentException e) {
                    String mavenCoordSource = mavenCoordsByProductIds.get(productId);
                    throw new GradleException(String.format("IllegalArgumentException encountered when generating "
                                    + "ProductDependency from recommended dependency for %s from %s",
                            productId, mavenCoordSource), e);
                }
            }
        }).collect(Collectors.toList());

        Set<String> unseenProductIds = new HashSet<>(recommendedDepsByProductId.keySet());
        unseenProductIds.removeAll(seenRecommendedProductIds);
        ignoredProductIds.get().forEach(ignored -> unseenProductIds.remove(ignored.toString()));
        unseenProductIds.remove(String.format("%s:%s", serviceGroup.get(), serviceName.get()));

        if (!unseenProductIds.isEmpty()) {
            throw new GradleException(String.format("The following products are recommended as dependencies but do not "
                    + "appear in the product dependencies or product dependencies ignored list: %s. See "
                    + "gradle-sls-packaging for more details", unseenProductIds));
        }

        jsonMapper.writeValue(getManifestFile(), SlsManifest.builder()
                .manifestVersion("1.0")
                .productType(productType.get())
                .productGroup(serviceGroup.get())
                .productName(serviceName.get())
                .productVersion(getProjectVersion())
                .putAllExtensions(manifestExtensions)
                .putExtensions("product-dependencies", dependencies)
                .build()
        );
    }

    private void validateProjectVersion() {
        String stringVersion = getProjectVersion();
        Preconditions.checkArgument(SlsProductVersions.isValidVersion(stringVersion),
                "Project version must be a valid SLS version: %s", stringVersion);
        if (!SlsProductVersions.isOrderableVersion(stringVersion)) {
            getProject().getLogger().warn(
                    "Version string in project {} is not orderable as per SLS specification: {}",
                    getProject().getName(), stringVersion);
        }
    }

    static String identifierToCoord(ModuleVersionIdentifier identifier) {
        return String.format("%s:%s:%s", identifier.getGroup(), identifier.getName(), identifier.getVersion());
    }

    public static TaskProvider<CreateManifestTask> createManifestTask(Project project, BaseDistributionExtension ext) {
        TaskProvider<CreateManifestTask> createManifest = project.getTasks().register(
                "createManifest", CreateManifestTask.class, task -> {
                    task.getServiceName().set(ext.getDistributionServiceName());
                    task.getServiceGroup().set(ext.getDistributionServiceGroup());
                    task.getProductType().set(ext.getProductType());
                    task.setManifestFile(new File(project.getBuildDir(), "/deployment/manifest.yml"));
                    task.getProductDependencies().set(ext.getProductDependencies());
                    task.setProductDependenciesConfig(ext.getProductDependenciesConfig());
                    task.getIgnoredProductIds().set(ext.getIgnoredProductDependencies());
                });
        project.afterEvaluate(p ->
                createManifest.configure(task -> task.setManifestExtensions(ext.getManifestExtensions())));
        return createManifest;
    }
}
