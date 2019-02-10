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
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.util.ConfigureUtil;

public class BaseDistributionExtension {

    private static final Pattern MAVEN_COORDINATE_PATTERN = Pattern.compile(""
            + "(?<group>[^:@?]*):"
            + "(?<name>[^:@?]*):"
            + "(?<version>[^:@?]*)"
            + "(:(?<classifier>[^:@?]*))?"
            + "(@(?<type>[^:@?]*))?");

    private final Property<String> serviceGroup;
    private final Property<String> serviceName;
    private final Property<String> podName;
    private final Property<ProductType> productType;
    private final ListProperty<ProductDependency> productDependencies;
    private final SetProperty<ProductId> ignoredProductDependencies;

    // TODO(forozco): Use MapProperty once our minimum supported version is 5.1
    private Map<String, Object> manifestExtensions;
    private Configuration productDependenciesConfig;

    @Inject
    public BaseDistributionExtension(Project project) {
        serviceGroup = project.getObjects().property(String.class);
        serviceName = project.getObjects().property(String.class);
        podName = project.getObjects().property(String.class);
        productType = project.getObjects().property(ProductType.class);
        productDependencies = project.getObjects().listProperty(ProductDependency.class);
        ignoredProductDependencies = project.getObjects().setProperty(ProductId.class);

        serviceGroup.set(project.provider(() -> project.getGroup().toString()));
        serviceName.set(project.provider(project::getName));
        podName.set(project.provider(project::getName));

        manifestExtensions = Maps.newHashMap();
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

    public final Provider<String> getPodName() {
        return podName;
    }

    public final void setPodName(String podName) {
        this.podName.set(podName);
    }

    public final Provider<ProductType> getProductType() {
        return productType;
    }

    public final void setProductType(ProductType productType) {
        this.productType.set(productType);
    }

    public final Provider<List<ProductDependency>> getProductDependencies() {
        return productDependencies;
    }

    public final void productDependency(String mavenCoordVersionRange) {
        productDependency(mavenCoordVersionRange, null);
    }

    public final void productDependency(String mavenCoordVersionRange, String recommendedVersion) {
        Matcher matcher = MAVEN_COORDINATE_PATTERN.matcher(mavenCoordVersionRange);
        Preconditions.checkArgument(matcher.matches(),
                "String '%s' is not a valid maven coordinate. "
                    + "Must be in the format 'group:name:version:classifier@type', where ':classifier' and '@type' are "
                    + "optional.", mavenCoordVersionRange);
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
        productDependency(dependencyGroup, dependencyName, minVersion, maxVersion,  null);
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
                maxVersion == null
                        ? generateMaxVersion(minVersion)
                        : maxVersion,
                recommendedVersion));
    }

    public final void productDependency(@DelegatesTo(ProductDependency.class) Closure closure) {
        ProductDependency dep = new ProductDependency();
        ConfigureUtil.configureUsing(closure).execute(dep);
        dep.isValid();
        productDependencies.add(dep);
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

    public final void ignoredProductDependency(@DelegatesTo(ProductId.class) Closure closure) {
        ProductId id = new ProductId();
        ConfigureUtil.configureUsing(closure).execute(id);
        id.isValid();
        this.ignoredProductDependencies.add(id);
    }

    public final Map<String, Object> getManifestExtensions() {
        return this.manifestExtensions;
    }

    public final void manifestExtensions(Map<String, Object> extensions) {
        manifestExtensions.putAll(extensions);
    }

    public final void setManifestExtension(String extensionName, Object extension) {
        manifestExtensions.put(extensionName, extension);
    }

    public final void setManifestExtensions(Map<String, Object> extensions) {
        manifestExtensions = extensions;
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
