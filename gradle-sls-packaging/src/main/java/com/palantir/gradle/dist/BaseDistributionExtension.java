/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.palantir.gradle.dist.artifacts.ArtifactLocator;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;

public class BaseDistributionExtension {

    private static final Pattern MAVEN_COORDINATE_PATTERN = Pattern.compile(""
            + "(?<group>[^:@?]*):"
            + "(?<name>[^:@?]*):"
            + "(?<version>[^:@?]*)"
            + "(:(?<classifier>[^:@?]*))?"
            + "(@(?<type>[^:@?]*))?");

    private static final ImmutableMap<String, Object> DEFAULT_MANIFEST_EXTENSIONS =
            ImmutableMap.of("require-individual-addressability", true);

    private final Project project;
    private final Property<String> serviceGroup;
    private final Property<String> serviceName;
    private final Property<ProductType> productType;
    private final ListProperty<ProductDependency> productDependencies;
    private final SetProperty<ProductId> optionalProductDependencies;
    private final SetProperty<ProductId> ignoredProductDependencies;
    private final DomainObjectSet<ArtifactLocator> artifacts;
    private final ProviderFactory providerFactory;
    private final MapProperty<String, Object> manifestExtensions;
    private final RegularFileProperty configurationYml;
    private final String projectName;
    private Configuration productDependenciesConfig;

    @Inject
    public BaseDistributionExtension(Project project) {
        this.project = project;
        providerFactory = project.getProviders();
        serviceGroup = project.getObjects().property(String.class);
        serviceName = project.getObjects().property(String.class);
        productType = project.getObjects().property(ProductType.class);
        productDependencies = project.getObjects().listProperty(ProductDependency.class);
        optionalProductDependencies = project.getObjects().setProperty(ProductId.class);
        ignoredProductDependencies = project.getObjects().setProperty(ProductId.class);
        artifacts = project.getObjects().domainObjectSet(ArtifactLocator.class);
        artifacts.configureEach(ArtifactLocator::isValid);

        serviceGroup.set(project.provider(() -> project.getGroup().toString()));
        serviceName.set(project.provider(project::getName));

        manifestExtensions =
                project.getObjects().mapProperty(String.class, Object.class).value(DEFAULT_MANIFEST_EXTENSIONS);

        configurationYml = project.getObjects().fileProperty().fileValue(project.file("deployment/configuration.yml"));

        projectName = project.getName();
    }

    public final Provider<String> getDistributionServiceGroup() {
        return serviceGroup;
    }

    /**
     * The group of the distribution being created.
     *
     * @deprecated Do not use this method directly, instead use getDistributionServiceGroup.
     */
    @Deprecated
    public final String getServiceGroup() {
        return serviceGroup.get();
    }

    public final void setServiceGroup(String serviceGroup) {
        this.serviceGroup.set(serviceGroup);
    }

    public final Provider<String> getDistributionServiceName() {
        return serviceName;
    }

    /**
     * The name of the distribution being created.
     *
     * @deprecated Do not use this method directly, instead use getDistributionServiceName.
     */
    @Deprecated
    public final String getServiceName() {
        return serviceName.get();
    }

    public final void setServiceName(String serviceName) {
        this.serviceName.set(serviceName);
    }

    public final Provider<ProductType> getProductType() {
        return productType;
    }

    public final void setProductType(ProductType productType) {
        this.productType.set(productType);
    }

    public final DomainObjectSet<ArtifactLocator> getArtifacts() {
        return artifacts;
    }

    /** Lazily configures and adds a {@link ArtifactLocator}. */
    public final void artifact(@DelegatesTo(ArtifactLocator.class) Closure<ArtifactLocator> closure) {
        ArtifactLocator artifactLocator = project.getObjects().newInstance(ArtifactLocator.class);
        project.configure(artifactLocator, closure);
        artifacts.add(artifactLocator);
    }

    public final void artifact(Action<ArtifactLocator> action) {
        ArtifactLocator artifactLocator = project.getObjects().newInstance(ArtifactLocator.class);
        action.execute(artifactLocator);
        artifacts.add(artifactLocator);
    }

    /**
     * The product dependencies of this distribution.
     *
     * @deprecated use {@link #getAllProductDependencies()} instead
     */
    @Deprecated
    public final Provider<List<ProductDependency>> getProductDependencies() {
        return productDependencies;
    }

    public final ListProperty<ProductDependency> getAllProductDependencies() {
        return productDependencies;
    }

    public final void productDependency(String mavenCoordVersionRange) {
        productDependency(mavenCoordVersionRange, null);
    }

    public final void productDependency(String mavenCoordVersionRange, String recommendedVersion) {
        Matcher matcher = MAVEN_COORDINATE_PATTERN.matcher(mavenCoordVersionRange);
        Preconditions.checkArgument(
                matcher.matches(),
                "String '%s' is not a valid maven coordinate. Must be in the format"
                        + " 'group:name:version:classifier@type', where ':classifier' and '@type' are optional.",
                mavenCoordVersionRange);
        String minVersion = matcher.group("version");
        productDependencies.add(new ProductDependency(
                matcher.group("group"),
                matcher.group("name"),
                minVersion,
                generateMaxVersion(minVersion),
                recommendedVersion));
    }

    public final void productDependency(String dependencyGroup, String dependencyName, String minVersion) {
        productDependency(dependencyGroup, dependencyName, minVersion, null, null);
    }

    public final void productDependency(
            String dependencyGroup, String dependencyName, String minVersion, String maxVersion) {
        productDependency(dependencyGroup, dependencyName, minVersion, maxVersion, null);
    }

    public final void productDependency(
            String dependencyGroup,
            String dependencyName,
            String minVersion,
            String maxVersion,
            String recommendedVersion) {
        productDependencies.add(new ProductDependency(
                dependencyGroup,
                dependencyName,
                minVersion,
                maxVersion == null ? generateMaxVersion(minVersion) : maxVersion,
                recommendedVersion));
    }

    /** Lazily configures and adds a {@link ProductDependency}. */
    public final void productDependency(@DelegatesTo(ProductDependency.class) Closure<ProductDependency> closure) {
        productDependencies.add(providerFactory.provider(() -> {
            ProductDependency dep = new ProductDependency();
            try {
                project.configure(dep, closure);
                if (dep.getMinimumVersion() != null && dep.getMaximumVersion() == null) {
                    dep.setMaximumVersion(generateMaxVersion(dep.getMinimumVersion()));
                }
                dep.isValid();
            } catch (Exception e) {
                throw new SafeRuntimeException(
                        "Error validating product dependency declared from project",
                        e,
                        SafeArg.of("projectName", projectName));
            }
            return dep;
        }));
    }

    public final Provider<Set<ProductId>> getOptionalProductDependencies() {
        return optionalProductDependencies;
    }

    public final void optionalProductDependency(String productGroup, String productName) {
        this.optionalProductDependencies.add(new ProductId(productGroup, productName));
    }

    public final void optionalProductDependency(String optionalProductId) {
        this.optionalProductDependencies.add(new ProductId(optionalProductId));
    }

    public final Provider<Set<ProductId>> getIgnoredProductDependencies() {
        return ignoredProductDependencies;
    }

    public final void ignoredProductDependency(String productGroup, String productName) {
        this.ignoredProductDependencies.add(new ProductId(productGroup, productName));
    }

    public final void ignoredProductDependency(String ignoredProductId) {
        this.ignoredProductDependencies.add(new ProductId(ignoredProductId));
    }

    public final void ignoredProductDependency(@DelegatesTo(ProductId.class) Closure<ProductId> closure) {
        ProductId id = new ProductId();
        project.configure(id, closure);
        id.isValid();
        this.ignoredProductDependencies.add(id);
    }

    public final MapProperty<String, Object> getManifestExtensions() {
        return this.manifestExtensions;
    }

    public final void manifestExtensions(Map<String, Object> extensions) {
        manifestExtensions.putAll(extensions);
    }

    public final void setManifestExtension(String extensionName, Object extension) {
        manifestExtensions.put(extensionName, extension);
    }

    public final void setManifestExtensions(Map<String, Object> extensions) {
        manifestExtensions.set(extensions);
    }

    public final RegularFileProperty getConfigurationYml() {
        return configurationYml;
    }

    public final Configuration getProductDependenciesConfig() {
        return productDependenciesConfig;
    }

    public final void setProductDependenciesConfig(Configuration productDependenciesConfig) {
        this.productDependenciesConfig = productDependenciesConfig;
    }

    static String generateMaxVersion(String minimumVersion) {
        return String.format("%s.x.x", Iterables.getFirst(Splitter.on(".").split(minimumVersion), null));
    }
}
