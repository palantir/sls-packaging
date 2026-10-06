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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/** A Gradle tar archive with optional, explicitly enabled pigz compression. */
@DisableCachingByDefault(because = "Matches Gradle's Tar caching behavior")
public abstract class DistributionTarTask extends Tar {
    public DistributionTarTask() {
        getUsePigz().convention(false);
        getPigzThreads().convention(4);
        getPigzExecutable().convention("pigz");
    }

    /** Whether to use pigz instead of Gradle's gzip compressor. */
    @Input
    public abstract Property<Boolean> getUsePigz();

    /** Maximum number of compression threads for this archive. */
    @Input
    public abstract Property<Integer> getPigzThreads();

    /** Executable name or absolute path to pigz. */
    @Input
    public abstract Property<String> getPigzExecutable();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Override
    protected final void copy() {
        if (!getUsePigz().get()) {
            super.copy();
            return;
        }
        if (getCompression() != Compression.GZIP) {
            throw new GradleException("usePigz requires distTar compression to be GZIP");
        }
        if (getPigzThreads().get() <= 0) {
            throw new GradleException("pigzThreads must be greater than zero");
        }

        Path archive = getArchiveFile().get().getAsFile().toPath();
        Path compressed = getTemporaryDir().toPath().resolve("distribution.tar.gz");
        try {
            // Retain Gradle's CopySpec handling, archive entries and permissions. Only compression changes.
            setCompression(Compression.NONE);
            super.copy();
            try (OutputStream output = Files.newOutputStream(compressed)) {
                getExecOperations()
                        .exec(spec -> {
                            spec.commandLine(
                                    getPigzExecutable().get(),
                                    "-n",
                                    "-p",
                                    getPigzThreads().get().toString(),
                                    "-c",
                                    "--",
                                    archive.toAbsolutePath().toString());
                            // Ambient compressor flags must not change the requested compression or output format.
                            spec.environment("GZIP", "");
                            spec.environment("PIGZ", "");
                            spec.setStandardOutput(output);
                        })
                        .assertNormalExitValue();
            }
            Files.move(compressed, archive, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            try {
                // A failed compressor must not leave an uncompressed file advertised as an SLS gzip archive.
                Files.deleteIfExists(archive);
                Files.deleteIfExists(compressed);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw new GradleException(
                    "Failed to create distribution using pigz; install the configured executable or disable usePigz",
                    failure);
        } finally {
            setCompression(Compression.GZIP);
        }
    }
}
