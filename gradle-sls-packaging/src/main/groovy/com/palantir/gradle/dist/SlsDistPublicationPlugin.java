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

import com.palantir.logsafe.Preconditions;
import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.util.GradleVersion;

/**
 * Sets up a dist publication that includes the product's SLS dependencies as gradle runtime dependencies.
 */
public class SlsDistPublicationPlugin implements Plugin<Project> {
    private static final String PUBLICATION_NAME = "slsDist";
    private static final GradleVersion MINIMUM_GRADLE_VERSION = GradleVersion.version("5.3");
    private final SoftwareComponentFactory componentFactory;

    @Inject
    public SlsDistPublicationPlugin(SoftwareComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
    }

    @Override
    public final void apply(Project project) {
        Preconditions.checkState(project.getPlugins().hasPlugin(SlsBaseDistPlugin.class),
                "This plugin must be applied through SlsBaseDistPlugin");
        project.getPluginManager().apply(ProductDependencyIntrospectionPlugin.class);

        // Created in SlsBaseDistPlugin
        NamedDomainObjectProvider<Configuration> outgoingConfiguration =
                project.getConfigurations().named(SlsBaseDistPlugin.SLS_CONFIGURATION_NAME);
        // Pick up product dependencies from the lock file, in order to publish them
        outgoingConfiguration.configure(conf -> {
            conf.extendsFrom(project
                    .getConfigurations()
                    .getByName(ProductDependencyIntrospectionPlugin.PRODUCT_DEPENDENCIES_CONFIGURATION));
        });

        project.getExtensions().configure(PublishingExtension.class, publishing -> {
            publishing.getPublications().create(PUBLICATION_NAME, MavenPublication.class, dist -> {
                AdhocComponentWithVariants component = componentFactory.adhoc("dist");
                // Note: both dependencies and outgoing artifacts are wired up from this configuration
                component.addVariantsFromConfiguration(
                        outgoingConfiguration.get(),
                        cvd -> cvd.mapToMavenScope("runtime"));
                dist.from(component);
            });
        });
    }

    static boolean canApply() {
        return GradleVersion.current().compareTo(MINIMUM_GRADLE_VERSION) >= 0;
    }
}
