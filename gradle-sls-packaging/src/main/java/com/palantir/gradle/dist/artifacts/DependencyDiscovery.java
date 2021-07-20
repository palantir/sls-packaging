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

    public static ArtifactView getFilteredArtifact(Project project, Configuration conf, String targetArtifact) {
        return conf.getIncoming().artifactView(v -> {
            v.getAttributes().attribute(ARTIFACT_FORMAT, targetArtifact);
            Category projectCategory =
                    project.getObjects().named(Category.class, PreferProjectCompatibilityRule.PROJECT);
            v.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, projectCategory);
        });
    }

    public static Configuration copyConfiguration(Project project, String configurationName, String name) {
        Configuration consumable = project.getConfigurations()
                .create(GUtil.toLowerCamelCase(configurationName + " for " + name), conf -> {
                    conf.extendsFrom(project.getConfigurations().getByName(configurationName));
                    conf.setDescription("DiagnosticsManifestPlugin uses this configuration to extract single file");
                    conf.setCanBeConsumed(true);
                    conf.setCanBeResolved(true);
                    conf.setVisible(false);
                });

        project.getDependencies().add(consumable.getName(), project);
        return consumable;
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
                            project.getObjects().named(Category.class, PreferProjectCompatibilityRule.EXTERNAL));
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
                            project.getObjects().named(Category.class, PreferProjectCompatibilityRule.PROJECT));
        });
    }

    private DependencyDiscovery() {}
}
