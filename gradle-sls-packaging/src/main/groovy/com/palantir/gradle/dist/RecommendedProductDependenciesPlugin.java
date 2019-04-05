/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import com.palantir.gradle.dist.tasks.CreateManifestTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.bundling.Jar;

public class RecommendedProductDependenciesPlugin implements Plugin<Project> {
    public final void apply(final Project project) {
        project.getPlugins().apply("java");
        final RecommendedProductDependenciesExtension ext = project
                .getExtensions()
                .create("recommendedProductDependencies", RecommendedProductDependenciesExtension.class, project);

        project.afterEvaluate(p -> {
            String recommendedProductDeps;
            try {
                recommendedProductDeps = new ObjectMapper().writeValueAsString(RecommendedProductDependencies
                        .builder()
                        .recommendedProductDependencies(ext.getRecommendedProductDependencies())
                        .build());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Couldn't serialize recommended product dependencies as string", e);
            }
            Jar jar = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
            jar
                    .getManifest()
                    .attributes(ImmutableMap.of(
                            CreateManifestTask.SLS_RECOMMENDED_PRODUCT_DEPS_KEY,
                            recommendedProductDeps));
        });
    }
}
