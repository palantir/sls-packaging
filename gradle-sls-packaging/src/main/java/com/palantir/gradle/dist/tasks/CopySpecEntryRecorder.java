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

// CHECKSTYLE.OFF: IllegalImport
package com.palantir.gradle.dist.tasks;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionExecuter;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.tasks.WorkResults;

// CHECKSTYLE.ON: IllegalImport
// Gradle's copy executer is the only way to resolve a copy spec exactly as Tar does, including directory entries,
// empty directories and duplicate handling; the public eachFile API only visits files. All internal API usage is kept
// in this class, loaded only when parallel archiving is enabled, so SlsDistTar can fall back to Gradle's own Tar action
// if
// these internals change.

/** Records the entries a copy spec resolves to, in archive order, instead of writing them. */
final class CopySpecEntryRecorder {
    private CopySpecEntryRecorder() {}

    /**
     * Records each entry to {@code manifest}. Entries whose content is transformed by the copy spec (e.g. by
     * {@code expand} or {@code filter}) have no source file holding their archived content, so it is written to
     * {@code filteredContents} on this thread, exactly as {@code Tar} would write it.
     */
    static void record(
            CopyActionExecuter executer,
            CopySpecInternal rootSpec,
            OptionalLong fixedTimestamp,
            File manifest,
            File filteredContents)
            throws IOException {
        deleteRecursively(filteredContents.toPath());
        Files.createDirectories(filteredContents.toPath());
        AtomicInteger filteredCount = new AtomicInteger();
        try (DataOutputStream output =
                new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(manifest.toPath())))) {
            CopyAction recorder = stream -> {
                stream.process(details -> {
                    try {
                        DistTarManifest.write(
                                output, toEntry(details, fixedTimestamp, filteredContents, filteredCount));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                return WorkResults.didWork(true);
            };
            executer.execute(rootSpec, recorder);
        }
    }

    private static DistTarManifest.Entry toEntry(
            FileCopyDetailsInternal details,
            OptionalLong fixedTimestamp,
            File filteredContents,
            AtomicInteger filteredCount) {
        long lastModified = fixedTimestamp.orElseGet(details::getLastModified);
        int mode = details.getPermissions().toUnixNumeric();
        String path = details.getRelativePath().getPathString();
        if (details.isDirectory()) {
            return new DistTarManifest.Entry(true, mode, lastModified, path + "/", "");
        }
        return new DistTarManifest.Entry(
                false, mode, lastModified, path, contentSource(details, filteredContents, filteredCount));
    }

    private static String contentSource(
            FileCopyDetailsInternal details, File filteredContents, AtomicInteger filteredCount) {
        try {
            return details.getFile().getAbsolutePath();
        } catch (UnsupportedOperationException e) {
            // Gradle refuses to expose the source file when content filters apply, because it isn't the archived
            // content
            File staged = new File(filteredContents, Integer.toString(filteredCount.incrementAndGet()));
            details.copyTo(staged);
            return staged.getAbsolutePath();
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
