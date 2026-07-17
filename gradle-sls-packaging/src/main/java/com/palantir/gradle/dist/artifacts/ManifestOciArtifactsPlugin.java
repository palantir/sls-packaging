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
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

public abstract class ManifestOciArtifactsPlugin implements Plugin<Project> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Override
    public final void apply(Project project) {
        NamedDomainObjectProvider<Configuration> declarable = project.getConfigurations()
                .register(ManifestOciArtifacts.DEPENDENCY_SCOPE, configuration -> {
                    configuration.setCanBeConsumed(false);
                    configuration.setCanBeResolved(false);
                    configuration.setDescription(
                            "Projects whose published OCI artifacts are added to this project's SLS manifest");
                });

        NamedDomainObjectProvider<Configuration> resolvable = project.getConfigurations()
                .register(ManifestOciArtifacts.RESOLVABLE, configuration -> {
                    configuration.setCanBeConsumed(false);
                    configuration.setCanBeResolved(true);
                    configuration.extendsFrom(declarable.get());
                    configuration
                            .getAttributes()
                            .attribute(
                                    Usage.USAGE_ATTRIBUTE,
                                    project.getObjects().named(Usage.class, ManifestOciArtifacts.USAGE));
                });

        project.getExtensions()
                .getByType(BaseDistributionExtension.class)
                .getArtifacts()
                .addAllLater(coordinateArtifacts(resolvable)
                        .flatMap(artifactCollection -> artifactLocators(artifactCollection, declarable)));
    }

    private static Provider<ArtifactCollection> coordinateArtifacts(
            NamedDomainObjectProvider<Configuration> resolvable) {
        return resolvable.map(configuration -> configuration
                .getIncoming()
                .artifactView(view -> view.lenient(true))
                .getArtifacts());
    }

    private Provider<List<ArtifactLocator>> artifactLocators(
            ArtifactCollection artifactCollection, Provider<Configuration> declarable) {
        Provider<Set<ResolvedArtifactResult>> artifacts = artifactCollection.getResolvedArtifacts();
        MapProperty<File, OciArtifactCoordinates> coordinatesByFile =
                getObjects().mapProperty(File.class, OciArtifactCoordinates.class);
        coordinatesByFile.set(artifacts.map(ManifestOciArtifactsPlugin::parseAndValidateArtifacts));
        coordinatesByFile.finalizeValueOnRead();

        return artifacts.zip(
                declarable,
                (resolvedArtifacts, configuration) ->
                        createArtifactLocators(resolvedArtifacts, configuration, coordinatesByFile));
    }

    private List<ArtifactLocator> createArtifactLocators(
            Set<ResolvedArtifactResult> artifacts,
            Configuration declarable,
            MapProperty<File, OciArtifactCoordinates> coordinatesByFile) {
        if (artifacts.isEmpty() && !declarable.getDependencies().isEmpty()) {
            throw noPublishedArtifacts();
        }

        return artifacts.stream()
                .map(ResolvedArtifactResult::getFile)
                .map(coordinatesByFile::getting)
                .map(this::artifactLocator)
                .toList();
    }

    private ArtifactLocator artifactLocator(Provider<OciArtifactCoordinates> coordinates) {
        PublishedOciArtifactLocator locator = getObjects().newInstance(PublishedOciArtifactLocator.class);
        locator.getType().set(coordinates.map(OciArtifactCoordinates::type));
        locator.getUri().set(coordinates.map(OciArtifactCoordinates::uri));
        locator.getPublish().set(coordinates.map(OciArtifactCoordinates::publish));
        return locator;
    }

    private static Map<File, OciArtifactCoordinates> parseAndValidateArtifacts(
            Set<ResolvedArtifactResult> artifactResults) {
        Map<File, OciArtifactCoordinates> artifacts = artifactResults.stream()
                .collect(Collectors.toMap(
                        ResolvedArtifactResult::getFile,
                        artifact -> parse(artifact.getFile().toPath())));
        if (artifacts.values().stream().noneMatch(OciArtifactCoordinates::publish)) {
            throw noPublishedArtifacts();
        }
        return artifacts;
    }

    private static GradleException noPublishedArtifacts() {
        return new GradleException("""
            No published OCI artifact coordinates were found for dependencies in the '%s' configuration. A dependency \
            must expose OCI artifact coordinates as a consumable configuration with the '%s' usage attribute, and at \
            least one matching artifact must be published.\
            """.formatted(ManifestOciArtifacts.DEPENDENCY_SCOPE, ManifestOciArtifacts.USAGE));
    }

    private static OciArtifactCoordinates parse(Path file) {
        try {
            return MAPPER.readValue(Files.readString(file), OciArtifactCoordinates.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read OCI artifact coordinates from " + file, e);
        }
    }

    interface PublishedOciArtifactLocator extends ArtifactLocator {
        @Input
        Property<Boolean> getPublish();

        @Override
        @Internal
        default boolean isPublished() {
            return getPublish().get();
        }
    }
}
