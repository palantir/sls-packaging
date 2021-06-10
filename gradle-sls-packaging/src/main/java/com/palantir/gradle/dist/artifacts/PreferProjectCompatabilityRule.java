/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.dist.artifacts;

import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.CompatibilityCheckDetails;

public final class PreferProjectCompatabilityRule implements AttributeCompatibilityRule<Category> {

    public static final String PROJECT = "project";
    public static final String EXTERNAL = "external";

    public static void configureRule(Project project) {
        AttributeMatchingStrategy<Category> categorySchema =
                project.getDependencies().getAttributesSchema().attribute(Category.CATEGORY_ATTRIBUTE);
        categorySchema
                .getCompatibilityRules()
                .add(
                        PreferProjectCompatabilityRule.class,
                        actionConfiguration -> actionConfiguration.params(
                                project.getObjects().named(Category.class, PROJECT),
                                project.getObjects().named(Category.class, EXTERNAL)));
    }

    private final Category project;
    private final Category external;

    @Inject
    PreferProjectCompatabilityRule(Category project, Category external) {
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
