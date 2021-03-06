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

package com.palantir.gradle.dist.artifacts;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.CompatibilityCheckDetails;

/**
 * PreferProjectCompatibilityRule works alongside artifactTransformers in {@link DependencyDiscovery} to ensure that
 * we apply the right transformation to project dependencies to avoid compilation.
 *
 * Since the consuming {@link org.gradle.api.artifacts.ArtifactView} specifies that it requires
 * {@link Category#CATEGORY_ATTRIBUTE} of {@link PreferProjectCompatibilityRule#PROJECT} Gradle will select outgoing
 * variants that have the attribute applied and ignore variants with other values. This rule will make it so that
 * we will fall back to accepting a variant with {@link Category#CATEGORY_ATTRIBUTE} of
 * {@link PreferProjectCompatibilityRule#EXTERNAL} if there is no other variants.
 */
public final class PreferProjectCompatibilityRule implements AttributeCompatibilityRule<Category> {

    public static final String PROJECT = "project";
    public static final String EXTERNAL = "external";

    public static void configureRule(Project project) {
        AttributeMatchingStrategy<Category> categorySchema =
                project.getDependencies().getAttributesSchema().attribute(Category.CATEGORY_ATTRIBUTE);
        categorySchema
                .getCompatibilityRules()
                .add(
                        PreferProjectCompatibilityRule.class,
                        actionConfiguration -> actionConfiguration.params(
                                project.getObjects().named(Category.class, PROJECT),
                                project.getObjects().named(Category.class, EXTERNAL)));
    }

    private final Category project;
    private final Category external;

    @Inject
    PreferProjectCompatibilityRule(Category project, Category external) {
        this.project = project;
        this.external = external;
    }

    @Override
    public void execute(CompatibilityCheckDetails<Category> details) {
        Category consumerValue = details.getConsumerValue();
        Category producerValue = details.getProducerValue();
        if (consumerValue == null) {
            details.compatible();
        } else if (project.equals(consumerValue) && external.equals(producerValue)) {
            details.compatible();
        }
    }
}
