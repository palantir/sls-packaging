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
import org.gradle.api.Project;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;

public class RecommendedProductDependenciesExtension {
    private final Project project;
    private final SetProperty<ProductDependency> recommendedProductDependencies;
    private final ProviderFactory providerFactory;

    @Inject
    public RecommendedProductDependenciesExtension(Project project) {
        this.project = project;
        this.recommendedProductDependencies =
                project.getObjects().setProperty(ProductDependency.class).empty();
        this.providerFactory = project.getProviders();
    }

    /** Lazily configures and adds a {@link ProductDependency}. */
    public final void productDependency(@DelegatesTo(ProductDependency.class) Closure<?> closure) {
        recommendedProductDependencies.add(providerFactory.provider(() -> {
            ProductDependency dep = new ProductDependency();
            project.configure(dep, closure);
            if (dep.getOptional()) {
                throw new IllegalArgumentException(String.format(
                        "Optional dependencies are not supported for recommended product "
                                + "dependencies. Please remove optional for dependency %s",
                        dep));
            }
            dep.isValid();
            return dep;
        }));
    }

    public final SetProperty<ProductDependency> getRecommendedProductDependenciesProvider() {
        return recommendedProductDependencies;
    }

    /**
     * The product dependencies of this project.
     *
     * @deprecated Use {@link #getRecommendedProductDependenciesProvider()} instead.
     */
    @Deprecated
    public final Set<ProductDependency> getRecommendedProductDependencies() {
        return recommendedProductDependencies.get();
    }
}
