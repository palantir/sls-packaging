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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.palantir.gradle.dist.BaseDistributionExtension;
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
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.util.GFileUtils;

public abstract class CreateManifestTask extends DefaultTask {
    private static final Logger log = Logging.getLogger(CreateManifestTask.class);
    public static final ObjectMapper jsonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerModule(new GuavaModule())
            .registerModule(new Jdk8Module());

    @Input
    abstract SetProperty<ProductId> getInRepoProductIds();

    @Input
    abstract Property<String> getServiceName();

    @Input
    abstract Property<String> getServiceGroup();

    @Input
    abstract Property<ProductType> getProductType();

    @Input
    abstract MapProperty<String, Object> getManifestExtensions();

    @Input
    abstract RegularFileProperty getProductDependenciesFile();

    @OutputFile
    abstract RegularFileProperty getManifestFile();

    @Input
    final String getProjectVersion() {
        return getProject().getVersion().toString();
    }

    /**
     * Intentionally checking whether file exists as gradle's {@link org.gradle.api.tasks.Optional} only operates on
     * whether the method returns null or not. Otherwise, it will fail when the file doesn't exist.
     */
    @InputFile
    @org.gradle.api.tasks.Optional
    final File getLockfileIfExists() {
        File file = getLockfile();
        if (file.exists()) {
            return file;
        }
        return null;
    }

    @TaskAction
    final void createManifest() throws IOException {
        validateProjectVersion();
        Preconditions.checkArgument(
                !getManifestExtensions().get().containsKey("product-dependencies"),
                "Use productDependencies configuration option instead of setting "
                        + "'product-dependencies' key in manifestExtensions");

        ProductDependencyManifest productDependencyManifest =
                jsonMapper.readValue(getProductDependenciesFile().get().getAsFile(), ProductDependencyManifest.class);

        List<ProductDependency> productDependencies = productDependencyManifest.productDependencies();
        if (productDependencies.isEmpty()) {
            requireAbsentLockfile();
        } else {
            ensureLockfileIsUpToDate(productDependencies);
        }

        jsonMapper.writeValue(
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

    private void requireAbsentLockfile() {
        File lockfile = getLockfile();
        Path relativePath = getProject().getRootDir().toPath().relativize(lockfile.toPath());

        if (!lockfile.exists()) {
            return;
        }

        if (getProject().getGradle().getStartParameter().isWriteDependencyLocks()) {
            lockfile.delete();
            getLogger().lifecycle("Deleted {}", relativePath);
        } else {
            throw new GradleException(String.format(
                    "%s must not exist, please run `./gradlew %s --write-locks` to delete it",
                    relativePath, getName()));
        }
    }

    private File getLockfile() {
        return getProject().file(ProductDependencyLockFile.LOCK_FILE);
    }

    private void ensureLockfileIsUpToDate(List<ProductDependency> productDeps) {
        File lockfile = getLockfile();
        Path relativePath = getProject().getRootDir().toPath().relativize(lockfile.toPath());
        String upToDateContents = ProductDependencyLockFile.asString(
                productDeps, getInRepoProductIds().get());
        boolean lockfileExists = lockfile.exists();

        if (getProject().getGradle().getStartParameter().isWriteDependencyLocks()) {
            GFileUtils.writeFile(upToDateContents, lockfile);
            if (!lockfileExists) {
                getLogger().lifecycle("Created {}\n\t{}", relativePath, upToDateContents.replaceAll("\n", "\n\t"));
            } else {
                getLogger().lifecycle("Updated {}", relativePath);
            }
        } else {
            if (!lockfileExists) {
                throw new GradleException(String.format(
                        "%s does not exist, please run `./gradlew %s --write-locks` and commit the resultant file",
                        relativePath, getName()));
            } else {
                String fromDisk = GFileUtils.readFile(lockfile);
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
            GFileUtils.writeFile(upToDateContents, tempFile);

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
            getLogger().debug("Unable to provide diff", e);
            return Optional.empty();
        }
    }

    private void validateProjectVersion() {
        String stringVersion = getProjectVersion();
        Preconditions.checkArgument(
                SlsVersion.check(stringVersion), "Project version must be a valid SLS version: %s", stringVersion);
        if (!OrderableSlsVersion.check(stringVersion)) {
            getProject()
                    .getLogger()
                    .warn(
                            "Version string in project {} is not orderable as per SLS specification: {}",
                            getProject().getName(),
                            stringVersion);
        }
    }

    public static TaskProvider<CreateManifestTask> createManifestTask(Project project, BaseDistributionExtension ext) {
        TaskProvider<ResolveProductDependenciesTask> resolveProductDependenciesTask =
                ProductDependencies.registerProductDependencyTasks(project, ext);

        TaskProvider<CreateManifestTask> createManifest = project.getTasks()
                .register("createManifest", CreateManifestTask.class, task -> {
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
                            return !project.getGradle().getStartParameter().isWriteDependencyLocks();
                        }
                    });

                    task.dependsOn(resolveProductDependenciesTask);
                });

        project.getPluginManager().withPlugin("lifecycle-base", _p -> {
            project.getTasks()
                    .named(LifecycleBasePlugin.CHECK_TASK_NAME)
                    .configure(task -> task.dependsOn(createManifest));
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
