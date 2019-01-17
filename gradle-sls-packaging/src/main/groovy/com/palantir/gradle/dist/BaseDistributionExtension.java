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

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.util.ConfigureUtil;

public class BaseDistributionExtension {

    private final Property<String> serviceGroup;
    private final Property<String> serviceName;
    private final Property<String> podName;
    private final Property<ProductType> productType;
    private final MapProperty<String, Object> manifestExtensions;
    private final ListProperty<ProductDependency> productDependencies;
    private final SetProperty<ProductId> ignoredProductIds;

    // TODO(forozco): can we kill this?
    private Configuration productDependenciesConfig;

    @Inject
    public BaseDistributionExtension(Project project) {
        serviceGroup = project.getObjects().property(String.class);
        serviceName = project.getObjects().property(String.class);
        podName = project.getObjects().property(String.class);
        productType = project.getObjects().property(ProductType.class);
        manifestExtensions = project.getObjects().mapProperty(String.class, Object.class).empty();
        productDependencies = project.getObjects().listProperty(ProductDependency.class).empty();
        ignoredProductIds = project.getObjects().setProperty(ProductId.class).empty();

        serviceGroup.set(project.provider(() -> project.getGroup().toString()));
        serviceName.set(project.provider(project::getName));
        podName.set(project.provider(project::getName));
    }

    public final Provider<String> getServiceGroup() {
        return serviceGroup;
    }

    public final void setServiceGroup(String serviceGroup) {
        this.serviceGroup.set(serviceGroup);
    }

    public final Provider<String> getServiceName() {
        return serviceName;
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

    public final void setProductDependency(@DelegatesTo(ProductDependency.class) Closure closure) {
        ProductDependency dep = new ProductDependency();
        ConfigureUtil.configureUsing(closure).execute(dep);
        dep.isValid();
        productDependencies.add(dep);
    }

    public final Provider<Set<ProductId>> getIgnoredProductIds() {
        return ignoredProductIds;
    }

    public final void setIgnoredProductIds(String ignoredProductId) {
        this.ignoredProductIds.add(new ProductId(ignoredProductId));
    }

    public final Provider<Map<String, Object>> getManifestExtensions() {
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
