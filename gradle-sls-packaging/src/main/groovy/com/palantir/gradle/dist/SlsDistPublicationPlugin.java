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
import com.palantir.logsafe.SafeArg;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.util.GradleVersion;

/**
 * Sets up an {@code slsDist} publication that is supposed to be configured later on with the right artifact/component.
 */
public class SlsDistPublicationPlugin implements Plugin<Project> {
    private static final GradleVersion MINIMUM_GRADLE_VERSION = GradleVersion.version("5.0");
    public static final String PUBLICATION_NAME = "dist";

    @Override
    public final void apply(Project project) {
        checkPreconditions(project);
        project.getPluginManager().apply(MavenPublishPlugin.class);

        project.getExtensions()
                .getByType(PublishingExtension.class)
                .getPublications()
                .create(PUBLICATION_NAME, MavenPublication.class);
    }

    public static void configurePublication(Project project, Action<MavenPublication> action) {
        project.getExtensions()
                .getByType(PublishingExtension.class)
                .getPublications()
                .named(PUBLICATION_NAME, MavenPublication.class, action);
    }

    private void checkPreconditions(Project project) {
        Preconditions.checkState(
                GradleVersion.current().compareTo(MINIMUM_GRADLE_VERSION) >= 0,
                "Cannot apply plugin since gradle version is too low",
                SafeArg.of("minimumGradleVersion", MINIMUM_GRADLE_VERSION));

        Preconditions.checkState(
                !project.getTasks().getNames().contains("distTar"),
                "Must apply com.palantir.sls-distribution-publication before creating distTar task");
    }
}
