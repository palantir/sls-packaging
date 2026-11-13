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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Writes a single-member gzip stream whose blocks are deflated concurrently, in the style of pigz.
 *
 * <p>Each full block is compressed independently as raw deflate data ending in a sync flush (so it ends on a byte
 * boundary), and the final block is terminated normally. The concatenated block outputs therefore form one valid
 * deflate stream, wrapped in a single gzip header and trailer.
 */
final class ParallelGzipOutputStream extends OutputStream {
    private static final byte[] GZIP_HEADER = {
        0x1f, (byte) 0x8b, Deflater.DEFLATED, 0, 0, 0, 0, 0, 0, (byte) 0xff,
    };

    private final OutputStream out;
    private final ExecutorService executor;
    private final int maxBlocksInFlight;
    private final ArrayDeque<Future<byte[]>> inFlight = new ArrayDeque<>();
    private final CRC32 crc = new CRC32();
    private final int blockSize;
    private byte[] buffer;
    private int count = 0;
    private long totalBytes = 0;
    private boolean closed = false;

    ParallelGzipOutputStream(OutputStream out, ExecutorService executor, int maxBlocksInFlight, int blockSize)
            throws IOException {
        this.out = out;
        this.executor = executor;
        this.maxBlocksInFlight = Math.max(1, maxBlocksInFlight);
        this.blockSize = blockSize;
        this.buffer = new byte[blockSize];
        out.write(GZIP_HEADER);
    }

    @Override
    public void write(int value) throws IOException {
        write(new byte[] {(byte) value}, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        crc.update(bytes, offset, length);
        totalBytes += length;
        int remaining = length;
        int position = offset;
        while (remaining > 0) {
            int chunk = Math.min(remaining, blockSize - count);
            System.arraycopy(bytes, position, buffer, count, chunk);
            count += chunk;
            position += chunk;
            remaining -= chunk;
            if (count == blockSize) {
                submitBlock(/* last= */ false);
            }
        }
    }

    private void submitBlock(boolean last) throws IOException {
        byte[] block = buffer;
        int length = count;
        buffer = new byte[blockSize];
        count = 0;
        inFlight.add(executor.submit(() -> deflate(block, length, last)));
        while (inFlight.size() >= maxBlocksInFlight) {
            writeOldestBlock();
        }
    }

    private void writeOldestBlock() throws IOException {
        try {
            out.write(inFlight.remove().get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while compressing");
        } catch (ExecutionException e) {
            throw new IOException("Failed to compress block", e.getCause());
        }
    }

    private static byte[] deflate(byte[] input, int length, boolean last) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, /* nowrap= */ true);
        try {
            deflater.setInput(input, 0, length);
            ByteArrayOutputStream compressed = new ByteArrayOutputStream(length / 2 + 64);
            byte[] chunk = new byte[64 * 1024];
            if (last) {
                deflater.finish();
                while (!deflater.finished()) {
                    compressed.write(chunk, 0, deflater.deflate(chunk));
                }
            } else {
                // A full output buffer means there may be more pending output for this flush
                int written;
                do {
                    written = deflater.deflate(chunk, 0, chunk.length, Deflater.SYNC_FLUSH);
                    compressed.write(chunk, 0, written);
                } while (written == chunk.length);
            }
            return compressed.toByteArray();
        } finally {
            deflater.end();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            submitBlock(/* last= */ true);
            while (!inFlight.isEmpty()) {
                writeOldestBlock();
            }
            writeIntLittleEndian((int) crc.getValue());
            writeIntLittleEndian((int) totalBytes);
        } finally {
            inFlight.forEach(future -> future.cancel(true));
            out.close();
        }
    }

    private void writeIntLittleEndian(int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 24) & 0xff);
    }
}
