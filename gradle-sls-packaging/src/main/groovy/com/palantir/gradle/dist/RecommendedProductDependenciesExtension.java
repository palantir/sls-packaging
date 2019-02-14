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

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.util.ConfigureUtil;

public class RecommendedProductDependenciesExtension {
    private final SetProperty<ProductDependency> recommendedProductDependencies;
    private final ProviderFactory providerFactory;

    @Inject
    public RecommendedProductDependenciesExtension(ObjectFactory objectFactory, ProviderFactory providerFactory) {
        this.recommendedProductDependencies = objectFactory.setProperty(ProductDependency.class).empty();
        this.providerFactory = providerFactory;
    }

    /**
     * Lazily configures and adds a {@link ProductDependency}.
     */
    public final void productDependency(@DelegatesTo(ProductDependency.class) Closure<?> closure) {
        recommendedProductDependencies.add(providerFactory.provider(() -> {
            ProductDependency dep = new ProductDependency();
            ConfigureUtil.configureUsing(closure).execute(dep);
            dep.isValid();
            return dep;
        }));
    }

    public final Set<ProductDependency> getRecommendedProductDependencies() {
        return recommendedProductDependencies.get();
    }
}
