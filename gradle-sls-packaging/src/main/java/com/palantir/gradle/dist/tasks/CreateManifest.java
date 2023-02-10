/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.tasks;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Streams;
import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import com.palantir.gradle.dist.ObjectMappers;
import com.palantir.gradle.dist.ProductDependency;
import com.palantir.gradle.dist.ProductDependencyLockFile;
import com.palantir.gradle.dist.ProductId;
import com.palantir.gradle.dist.ProductType;
import com.palantir.gradle.dist.SlsManifest;
import com.palantir.gradle.dist.pdeps.ProductDependencyManifest;
import com.palantir.sls.versions.OrderableSlsVersion;
import com.palantir.sls.versions.SlsVersion;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;

@AutoParallelizable
final class CreateManifest {
    private static final Logger logger = Logging.getLogger(CreateManifest.class);

    interface Params {
        @Input
        SetProperty<ProductId> getInRepoProductIds();

        @Input
        Property<String> getServiceName();

        @Input
        Property<String> getServiceGroup();

        @Input
        Property<ProductType> getProductType();

        @Input
        MapProperty<String, Object> getManifestExtensions();

        @InputFile
        RegularFileProperty getProductDependenciesFile();

        @OutputFile
        RegularFileProperty getManifestFile();

        @Input
        Property<String> getProjectVersion();

        default Provider<RegularFile> getLockfile() {
            return getProjectDir().map(projectDir -> projectDir.file(ProductDependencyLockFile.LOCK_FILE));
        }

        default boolean lockfileExists() {
            return getLockfile().get().getAsFile().exists();
        }

        @Input
        Property<Boolean> getShouldWriteLocks();

        @Internal
        Property<String> getTaskName();

        @Internal
        DirectoryProperty getRootDir();

        @Internal
        DirectoryProperty getProjectDir();
    }

    static void action(Params params) {
        validateProjectVersion(params.getProjectVersion());
        Preconditions.checkArgument(
                !params.getManifestExtensions().get().containsKey("product-dependencies"),
                "Use productDependencies configuration option instead of setting "
                        + "'product-dependencies' key in manifestExtensions");

        ProductDependencyManifest productDependencyManifest = ObjectMappers.readProductDependencyManifest(
                params.getProductDependenciesFile().getAsFile().get());

        List<ProductDependency> productDependencies = productDependencyManifest.productDependencies();
        if (productDependencies.isEmpty()) {
            requireAbsentLockfile(params);
        } else {
            ensureLockfileIsUpToDate(params, productDependencies);
        }

        try {
            ObjectMappers.jsonMapper.writeValue(
                    params.getManifestFile().getAsFile().get(),
                    SlsManifest.builder()
                            .manifestVersion("1.0")
                            .productType(params.getProductType().get())
                            .productGroup(params.getServiceGroup().get())
                            .productName(params.getServiceName().get())
                            .productVersion(params.getProjectVersion().get())
                            .putAllExtensions(params.getManifestExtensions().get())
                            .putExtensions("product-dependencies", productDependencies)
                            .build());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write json for manifest", e);
        }
    }

    private static void requireAbsentLockfile(Params params) {
        if (!params.lockfileExists()) {
            return;
        }

        File lockfile = params.getLockfile().get().getAsFile();

        if (params.getShouldWriteLocks().get()) {
            lockfile.delete();
            logger.lifecycle("Deleted {}", lockfile);
        } else {
            throw new GradleException(String.format(
                    "%s must not exist, please run `./gradlew %s --write-locks` to delete it",
                    lockfile, params.getTaskName().get()));
        }
    }

    private static void ensureLockfileIsUpToDate(Params params, List<ProductDependency> productDeps) {
        File lockfile = params.getLockfile().get().getAsFile();
        Path relativePath = params.getRootDir().getAsFile().get().toPath().relativize(lockfile.toPath());

        String upToDateContents = ProductDependencyLockFile.asString(
                productDeps, params.getInRepoProductIds().get());

        if (params.getShouldWriteLocks().get()) {
            try {
                Files.writeString(lockfile.toPath(), upToDateContents);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write lockfile " + relativePath, e);
            }

            if (!params.lockfileExists()) {
                logger.lifecycle("Created {}\n\t{}", relativePath, upToDateContents.replaceAll("\n", "\n\t"));
            } else {
                logger.lifecycle("Updated {}", relativePath);
            }
        } else {
            if (!params.lockfileExists()) {
                throw new GradleException(String.format(
                        "%s does not exist, please run `./gradlew %s --write-locks` and commit the resultant file",
                        relativePath, params.getTaskName().get()));
            } else {
                try {
                    String fromDisk = Files.readString(lockfile.toPath());

                    Preconditions.checkState(
                            fromDisk.equals(upToDateContents),
                            "%s is out of date, please run `./gradlew %s --write-locks` to update it%s",
                            relativePath,
                            params.getTaskName(),
                            diff(params, lockfile, upToDateContents)
                                    .map(s -> ":\n" + s)
                                    .orElse(""));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read lockfile " + relativePath, e);
                }
            }
        }
    }

    /** Provide a rich diff so the user understands what change will be made before they run --write-locks. */
    private static Optional<String> diff(Params params, File existing, String upToDateContents) {
        try {
            File tempFile = Files.createTempFile("product-dependencies", "lock").toFile();
            Files.writeString(tempFile.toPath(), upToDateContents);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            //            params.getExecOperations().exec(spec -> {
            //                spec.commandLine("diff", "-u", existing.getAbsolutePath(), tempFile.getAbsolutePath());
            //                spec.setStandardOutput(baos);
            //                spec.setIgnoreExitValue(true);
            //            });
            return Optional.of(
                    Streams.stream(Splitter.on("\n").split(new String(baos.toByteArray(), StandardCharsets.UTF_8)))
                            .skip(2)
                            .collect(Collectors.joining("\n")));
        } catch (IOException e) {
            logger.debug("Unable to provide diff", e);
            return Optional.empty();
        }
    }

    private static void validateProjectVersion(Provider<String> projectVersion) {
        String stringVersion = projectVersion.get();
        Preconditions.checkArgument(
                SlsVersion.check(stringVersion), "Project version must be a valid SLS version: %s", stringVersion);
        if (!OrderableSlsVersion.check(stringVersion)) {
            logger.info("Version string is not orderable as per SLS specification: {}", stringVersion);
        }
    }

    private CreateManifest() {}
}
