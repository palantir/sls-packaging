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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

/** Writes the gzipped tar described by a manifest produced by {@link SlsDistTar}. */
public abstract class WriteDistTarWorkAction implements WorkAction<WriteDistTarWorkAction.Parameters> {
    private static final int BLOCK_SIZE = 1024 * 1024;

    // Bounds each archive's buffered blocks (~2 MiB each, input plus output) however many tasks run concurrently
    private static final int MAX_BLOCKS_IN_FLIGHT_PER_ARCHIVE = 16;

    public interface Parameters extends WorkParameters {
        RegularFileProperty getManifest();

        RegularFileProperty getArchiveFile();

        Property<DistTarCompressionService> getCompressionService();
    }

    @Override
    public final void execute() {
        File archive = getParameters().getArchiveFile().get().getAsFile();
        File temporaryArchive = new File(archive.getParentFile(), archive.getName() + ".tmp");
        try {
            writeArchive(temporaryArchive);
            Files.move(temporaryArchive.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temporaryArchive, e);
            throw new UncheckedIOException("Failed to write " + archive, e);
        } catch (RuntimeException e) {
            deleteQuietly(temporaryArchive, e);
            throw e;
        }
    }

    private void writeArchive(File destination) throws IOException {
        DistTarCompressionService compression =
                getParameters().getCompressionService().get();
        int maxBlocksInFlight = Math.min(2 * compression.threads(), MAX_BLOCKS_IN_FLIGHT_PER_ARCHIVE);
        File manifest = getParameters().getManifest().get().getAsFile();
        try (DataInputStream entries =
                        new DataInputStream(new BufferedInputStream(Files.newInputStream(manifest.toPath())));
                TarArchiveOutputStream tar = new TarArchiveOutputStream(new BufferedOutputStream(
                        new ParallelGzipOutputStream(
                                Files.newOutputStream(destination.toPath()),
                                compression.executor(),
                                maxBlocksInFlight,
                                BLOCK_SIZE),
                        BLOCK_SIZE))) {
            // Same settings as Gradle's TarCopyAction
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR);
            while (true) {
                DistTarManifest.Entry entry;
                try {
                    entry = DistTarManifest.read(entries);
                } catch (EOFException e) {
                    break;
                }
                writeEntry(tar, entry);
            }
            tar.finish();
        }
    }

    private static void writeEntry(TarArchiveOutputStream tar, DistTarManifest.Entry entry) throws IOException {
        TarArchiveEntry archiveEntry = new TarArchiveEntry(entry.path());
        archiveEntry.setModTime(entry.lastModifiedMillis());
        if (entry.isDirectory()) {
            archiveEntry.setMode(UnixStat.DIR_FLAG | entry.mode());
            tar.putArchiveEntry(archiveEntry);
        } else {
            File source = new File(entry.sourcePath());
            archiveEntry.setMode(UnixStat.FILE_FLAG | entry.mode());
            archiveEntry.setSize(source.length());
            tar.putArchiveEntry(archiveEntry);
            try (InputStream input = Files.newInputStream(source.toPath())) {
                input.transferTo(tar);
            }
        }
        tar.closeArchiveEntry();
    }

    private static void deleteQuietly(File file, Exception cause) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            cause.addSuppressed(e);
        }
    }
}
