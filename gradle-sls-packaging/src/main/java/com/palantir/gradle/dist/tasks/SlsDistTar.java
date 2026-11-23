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

package com.palantir.gradle.dist.tasks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.OptionalLong;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkerExecutor;

/**
 * The {@code distTar} task. Behaves exactly like {@link Tar} unless {@link #getParallelArchiving()} is enabled.
 *
 * <p>A plain {@link Tar} task writes and compresses the archive synchronously while holding its project's lock, which
 * for multi-GB distributions blocks every other task in the project (including Worker API tasks such as docker
 * builds, which cannot complete without the lock) for minutes. With parallel archiving enabled, the copy spec is still
 * resolved by Gradle's own copy machinery on the task thread, but the archive is written by a Worker API action with
 * parallel gzip compression, which releases the project lock. The uncompressed tar contents are byte-identical.
 */
@DisableCachingByDefault(because = "Not worth caching; matches Gradle's Tar task")
public abstract class SlsDistTar extends Tar {
    /** Gradle property that sets the default for {@link #getParallelArchiving()}. */
    public static final String PARALLEL_ARCHIVING_PROPERTY = "com.palantir.sls-packaging.parallel-dist-tar";

    // Matches Gradle's TarCopyAction.CONSTANT_TIME_FOR_TAR_ENTRIES (1970-01-02T00:00:00Z)
    private static final long CONSTANT_TIME_FOR_TAR_ENTRIES = 86_400_000L;

    /**
     * Whether to write the archive with the Worker API and parallel gzip. Changes the compressed bytes (but not the
     * uncompressed tar), so it is an input. Only applies to gzip compression.
     */
    @Input
    public abstract Property<Boolean> getParallelArchiving();

    /** Compression threads shared by all parallel-archiving distTar tasks in the build. Does not affect the output. */
    @Internal
    public abstract Property<DistTarCompressionService> getCompressionService();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    public static void configureConventions(Project project, SlsDistTar task) {
        task.getParallelArchiving()
                .convention(project.getProviders()
                        .gradleProperty(PARALLEL_ARCHIVING_PROPERTY)
                        .map(Boolean::parseBoolean)
                        .orElse(false));
        Provider<DistTarCompressionService> compressionService = DistTarCompressionService.register(project);
        task.getCompressionService().convention(compressionService);
        task.usesService(compressionService);
    }

    @Override
    @TaskAction
    protected final void copy() {
        if (!getParallelArchiving().get() || getCompression() != Compression.GZIP) {
            super.copy();
            return;
        }

        File archive = getArchiveFile().get().getAsFile();
        File manifest = new File(getTemporaryDir(), "manifest.bin");
        File filteredContents = new File(getTemporaryDir(), "filtered");
        try {
            Files.createDirectories(archive.getParentFile().toPath());
            Files.deleteIfExists(archive.toPath());
            CopySpecEntryRecorder.record(
                    createCopyActionExecuter(), getRootSpec(), fixedEntryTimestamp(), manifest, filteredContents);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (LinkageError e) {
            getLogger()
                    .warn(
                            "Gradle internals used by {} have changed; falling back to writing the archive"
                                    + " synchronously",
                            PARALLEL_ARCHIVING_PROPERTY,
                            e);
            super.copy();
            return;
        }

        getLogger().info("Writing {} on a worker thread with parallel gzip compression", archive);
        getWorkerExecutor().noIsolation().submit(WriteDistTarWorkAction.class, parameters -> {
            parameters.getManifest().set(manifest);
            parameters.getArchiveFile().set(archive);
            parameters.getCompressionService().set(getCompressionService());
        });
    }

    /** Mirrors how Gradle's TarCopyAction picks entry timestamps. */
    private OptionalLong fixedEntryTimestamp() {
        if (isPreserveFileTimestamps()) {
            return OptionalLong.empty();
        }
        Long configured = null;
        try {
            // Only exists on newer Gradle versions; older versions always use the constant
            configured = getReproducibleFileTimestamp().getOrNull();
        } catch (NoSuchMethodError | AbstractMethodError e) {
            // Fall through to the constant timestamp
        }
        return OptionalLong.of(
                configured == null
                        ? CONSTANT_TIME_FOR_TAR_ENTRIES
                        : Math.max(configured, CONSTANT_TIME_FOR_TAR_ENTRIES));
    }
}
