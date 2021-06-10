/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.dist.artifacts;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.function.Consumer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.util.GUtil;

public final class DependencyDiscovery {
    public static final Attribute<String> ARTIFACT_FORMAT = Attribute.of("artifactType", String.class);
    public static final String PRODUCT_DEPENDENCIES = "product-dependencies";

    public static ArtifactView getFilteredArtifact(Project project, Configuration conf, String targetArtifact) {
        return conf.getIncoming().artifactView(v -> {
            v.getAttributes().attribute(ARTIFACT_FORMAT, targetArtifact);
            Category projectCategory =
                    project.getObjects().named(Category.class, PreferProjectCompatabilityRule.PROJECT);
            v.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, projectCategory);
        });
    }

    public static Configuration copyClasspath(Project project, String name) {
        Configuration conf = project.getConfigurations()
                .create(GUtil.toLowerCamelCase("runtimeClasspathFor " + name), c -> {
                    c.setCanBeConsumed(true);
                    c.setCanBeResolved(true);
                    c.setVisible(false);
                });
        Map<String, String> projectDependency =
                ImmutableMap.of("path", project.getPath(), "configuration", "runtimeElements");
        project.getDependencies().add(conf.getName(), project.getDependencies().project(projectDependency));
        return conf;
    }

    public static <T extends TransformAction<P>, P extends TransformParameters> void configureJarTransform(
            Project project, Class<T> clazz, String targetArtifact, Consumer<P> configureParameters) {
        project.getDependencies().registerTransform(clazz, details -> {
            configureParameters.accept(details.getParameters());
            details.getFrom().attribute(ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE);
            details.getTo().attribute(ARTIFACT_FORMAT, targetArtifact);

            details.getFrom()
                    .attribute(
                            Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            details.getTo()
                    .attribute(
                            Category.CATEGORY_ATTRIBUTE,
                            project.getObjects().named(Category.class, PreferProjectCompatabilityRule.EXTERNAL));
        });
    }

    public static <T extends TransformAction<P>, P extends TransformParameters> void configureResourceTransform(
            Project project, Class<T> clazz, String targetArtifact, Consumer<P> configureParameters) {
        project.getDependencies().registerTransform(clazz, details -> {
            configureParameters.accept(details.getParameters());

            details.getFrom().attribute(ARTIFACT_FORMAT, ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY);
            details.getTo().attribute(ARTIFACT_FORMAT, targetArtifact);

            details.getFrom()
                    .attribute(
                            Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
            details.getTo()
                    .attribute(
                            Category.CATEGORY_ATTRIBUTE,
                            project.getObjects().named(Category.class, PreferProjectCompatabilityRule.PROJECT));
        });
    }

    private DependencyDiscovery() {}
}
