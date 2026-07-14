/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.gradle.dist.BaseDistributionExtension;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;

public abstract class ManifestOciArtifactsPlugin implements Plugin<Project> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Override
    public final void apply(Project project) {
        NamedDomainObjectProvider<Configuration> manifestOciArtifacts = project.getConfigurations()
                .register(ManifestOciArtifacts.CONFIGURATION_NAME, configuration -> {
                    configuration.setCanBeConsumed(false);
                    configuration.setCanBeResolved(false);
                    configuration.setDescription(
                            "Projects whose published OCI artifacts are added to this project's SLS manifest");
                });

        NamedDomainObjectProvider<Configuration> manifestOciArtifactsResolvable = project.getConfigurations()
                .register(ManifestOciArtifacts.RESOLVABLE_CONFIGURATION_NAME, configuration -> {
                    configuration.setCanBeConsumed(false);
                    configuration.setCanBeResolved(true);
                    configuration.extendsFrom(manifestOciArtifacts.get());
                    configuration
                            .getAttributes()
                            .attribute(
                                    Usage.USAGE_ATTRIBUTE,
                                    project.getObjects().named(Usage.class, ManifestOciArtifacts.USAGE));
                });

        Provider<List<ArtifactLocator>> artifactLocators = manifestArtifactLocators(manifestOciArtifactsResolvable);
        project.getExtensions()
                .getByType(BaseDistributionExtension.class)
                .getArtifacts()
                .addAll(artifactLocators);
    }

    private Provider<List<ArtifactLocator>> manifestArtifactLocators(
            NamedDomainObjectProvider<Configuration> resolvable) {
        return coordinateArtifacts(resolvable)
                .flatMap(ArtifactCollection::getResolvedArtifacts)
                .zip(resolvable, this::toArtifactLocators);
    }

    private static Provider<ArtifactCollection> coordinateArtifacts(
            NamedDomainObjectProvider<Configuration> resolvable) {
        return resolvable.map(configuration -> configuration
                .getIncoming()
                .artifactView(view -> view.lenient(true))
                .getArtifacts());
    }

    private List<ArtifactLocator> toArtifactLocators(
            Set<ResolvedArtifactResult> coordinateArtifacts, Configuration resolvable) {
        List<ArtifactLocator> published = coordinateArtifacts.stream()
                .map(location -> parse(location.getFile().toPath()))
                .filter(OciArtifactCoordinates::publish)
                .map(this::artifactLocator)
                .toList();

        if (published.isEmpty() && !resolvable.getAllDependencies().isEmpty()) {
            throw noPublishedArtifacts();
        }
        return published;
    }

    private ArtifactLocator artifactLocator(OciArtifactCoordinates coordinates) {
        ArtifactLocator locator = getObjects().newInstance(ArtifactLocator.class);
        locator.getType().set(coordinates.type());
        locator.getUri().set(coordinates.uri());
        return locator;
    }

    private static GradleException noPublishedArtifacts() {
        return new GradleException("""
            No published OCI artifact coordinates were found for dependencies in the '%s' configuration. A dependency \
            must expose OCI artifact coordinates as a consumable configuration with the '%s' usage attribute, and at \
            least one matching artifact must be published.\
            """.formatted(ManifestOciArtifacts.CONFIGURATION_NAME, ManifestOciArtifacts.USAGE));
    }

    private static OciArtifactCoordinates parse(Path file) {
        try {
            return MAPPER.readValue(Files.readString(file), OciArtifactCoordinates.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read OCI artifact coordinates from " + file, e);
        }
    }
}
