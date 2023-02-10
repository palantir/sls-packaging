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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.palantir.gradle.dist.BaseDistributionExtension;
import com.palantir.gradle.dist.ObjectMappers;
import com.palantir.gradle.dist.ProductDependency;
import com.palantir.gradle.dist.ProductDependencyIntrospectionPlugin;
import com.palantir.gradle.dist.ProductDependencyLockFile;
import com.palantir.gradle.dist.ProductId;
import com.palantir.gradle.dist.ProductType;
import com.palantir.gradle.dist.SlsManifest;
import com.palantir.gradle.dist.pdeps.ProductDependencies;
import com.palantir.gradle.dist.pdeps.ProductDependencyManifest;
import com.palantir.gradle.dist.pdeps.ResolveProductDependenciesTask;
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
import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

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

        /**
         * Intentionally checking whether file exists as gradle's {@link org.gradle.api.tasks.Optional} only operates on
         * whether the method returns null or not. Otherwise, it will fail when the file doesn't exist.
         */
        @InputFile
        @org.gradle.api.tasks.Optional
        RegularFileProperty getLockfileIfExists();

        @Input
        Property<Boolean> getShouldWriteLocks();

        @Internal
        Property<String> getTaskName();
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
            ensureLockfileIsUpToDate(productDependencies);
        }

        ObjectMappers.jsonMapper.writeValue(
                getManifestFile().getAsFile().get(),
                SlsManifest.builder()
                        .manifestVersion("1.0")
                        .productType(getProductType().get())
                        .productGroup(getServiceGroup().get())
                        .productName(getServiceName().get())
                        .productVersion(getProjectVersion())
                        .putAllExtensions(getManifestExtensions().get())
                        .putExtensions("product-dependencies", productDependencies)
                        .build());
    }

    private static void requireAbsentLockfile(Params params) {
        File lockfile = getLockfile();

        if (!lockfile.exists()) {
            return;
        }

        if (params.getShouldWriteLocks().get()) {
            lockfile.delete();
            logger.lifecycle("Deleted {}", lockfile);
        } else {
            throw new GradleException(String.format(
                    "%s must not exist, please run `./gradlew %s --write-locks` to delete it",
                    lockfile, params.getTaskName().get()));
        }
    }

    private File getLockfile() {
        return getProject().file(ProductDependencyLockFile.LOCK_FILE);
    }

    public static boolean shouldWriteLocks(Project project) {
        String taskName = project.getPath().equals(":")
                ? ":writeProductDependenciesLocks"
                : project.getPath() + ":writeProductDependenciesLocks";
        Gradle gradle = project.getGradle();
        return gradle.getStartParameter().isWriteDependencyLocks()
                || gradle.getTaskGraph().hasTask(taskName);
    }

    private static void ensureLockfileIsUpToDate(Params params, List<ProductDependency> productDeps)
            throws IOException {
        File lockfile = getLockfile();
        Path relativePath = getProject().getRootDir().toPath().relativize(lockfile.toPath());
        String upToDateContents = ProductDependencyLockFile.asString(
                productDeps, params.getInRepoProductIds().get());
        boolean lockfileExists = lockfile.exists();

        if (params.getShouldWriteLocks().get()) {
            Files.writeString(lockfile.toPath(), upToDateContents);

            if (!lockfileExists) {
                logger.lifecycle("Created {}\n\t{}", relativePath, upToDateContents.replaceAll("\n", "\n\t"));
            } else {
                logger.lifecycle("Updated {}", relativePath);
            }
        } else {
            if (!lockfileExists) {
                throw new GradleException(String.format(
                        "%s does not exist, please run `./gradlew %s --write-locks` and commit the resultant file",
                        relativePath, params.getTaskName().get()));
            } else {
                String fromDisk = Files.readString(lockfile.toPath());
                Preconditions.checkState(
                        fromDisk.equals(upToDateContents),
                        "%s is out of date, please run `./gradlew %s --write-locks` to update it%s",
                        relativePath,
                        getName(),
                        diff(lockfile, upToDateContents).map(s -> ":\n" + s).orElse(""));
            }
        }
    }

    /** Provide a rich diff so the user understands what change will be made before they run --write-locks. */
    private Optional<String> diff(File existing, String upToDateContents) {
        try {
            File tempFile = Files.createTempFile("product-dependencies", "lock").toFile();
            Files.writeString(tempFile.toPath(), upToDateContents);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            getProject().exec(spec -> {
                spec.commandLine("diff", "-u", existing.getAbsolutePath(), tempFile.getAbsolutePath());
                spec.setStandardOutput(baos);
                spec.setIgnoreExitValue(true);
            });
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

    public static TaskProvider<CreateManifest> createManifestTask(Project project, BaseDistributionExtension ext) {
        TaskProvider<ResolveProductDependenciesTask> resolveProductDependenciesTask =
                ProductDependencies.registerProductDependencyTasks(project, ext);

        TaskProvider<CreateManifest> createManifest = project.getTasks()
                .register("createManifest", CreateManifest.class, task -> {
                    task.getServiceName().set(ext.getDistributionServiceName());
                    task.getServiceGroup().set(ext.getDistributionServiceGroup());
                    task.getProductType().set(ext.getProductType());
                    task.getManifestFile().set(new File(project.getBuildDir(), "/deployment/manifest.yml"));
                    task.getProductDependenciesFile()
                            .set(resolveProductDependenciesTask.flatMap(
                                    ResolveProductDependenciesTask::getManifestFile));
                    task.getManifestExtensions().set(ext.getManifestExtensions());
                    task.getInRepoProductIds()
                            .set(project.provider(() -> ProductDependencyIntrospectionPlugin.getInRepoProductIds(
                                            project.getRootProject())
                                    .keySet()));

                    // Ensure we re-run task to write locks
                    task.getOutputs().upToDateWhen(new Spec<Task>() {
                        @Override
                        public boolean isSatisfiedBy(Task _task) {
                            return !shouldWriteLocks(project);
                        }
                    });

                    task.dependsOn(resolveProductDependenciesTask);
                });

        project.getPluginManager().withPlugin("lifecycle-base", _p -> {
            project.getTasks()
                    .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                    .configure(task -> task.dependsOn(createManifest));
        });

        project.getTasks()
                .register("writeProductDependenciesLocks", WriteProductDependenciesLocksMarkerTask.class, task -> {
                    task.dependsOn(createManifest);
                });

        // We want `./gradlew --write-locks` to magically fix up the product-dependencies.lock file
        // We can't do this at configuration time because it would mess up gradle-consistent-versions.
        StartParameter startParam = project.getGradle().getStartParameter();
        if (startParam.isWriteDependencyLocks() && !startParam.getTaskNames().contains("createManifest")) {
            List<String> taskNames = ImmutableList.<String>builder()
                    .addAll(startParam.getTaskNames())
                    .add("createManifest")
                    .build();
            startParam.setTaskNames(taskNames);
        }

        return createManifest;
    }
}
