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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.palantir.logsafe.Preconditions;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;

/**
 * This task is only necessary because {@link Jar#getManifest()} cannot be configured lazily at configuration-time, so
 * we have to configure it at execution-time instead.
 */
public class ConfigureProductDependenciesTask extends DefaultTask {

    private final Jar jar;
    private final SetProperty<ProductDependency> productDependencies =
            getProject().getObjects().setProperty(ProductDependency.class);

    public ConfigureProductDependenciesTask(Jar jar) {
        setDescription("Configures the 'jar' task to write the input product dependencies into its manifest");

        this.jar = jar;
        jar.dependsOn(this);
    }

    @TaskAction
    final void action() {
        Preconditions.checkState(
                !jar.getState().getExecuted(), "Attempted to configure jar task after it was executed");
        jar.getManifest().from(createManifest(getProject(), productDependencies.get()));
    }

    public final void setProductDependencies(Provider<Set<ProductDependency>> productDependencies) {
        this.productDependencies.set(productDependencies);
    }

    /** Eagerly creates a manifest containing <b>only</b> the recommended product dependencies. */
    private static Manifest createManifest(Project project, Set<ProductDependency> recommendedProductDependencies) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        return javaConvention.manifest(manifest -> {
            String recommendedProductDeps;
            try {
                recommendedProductDeps = new ObjectMapper()
                        .writeValueAsString(RecommendedProductDependencies.builder()
                                .recommendedProductDependencies(recommendedProductDependencies)
                                .build());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Couldn't serialize recommended product dependencies as string", e);
            }
            manifest.attributes(ImmutableMap.of(
                    RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY, recommendedProductDeps));
        });
    }
}
