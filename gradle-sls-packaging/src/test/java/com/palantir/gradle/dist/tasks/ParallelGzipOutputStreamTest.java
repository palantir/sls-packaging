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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParallelGzipOutputStreamTest {
    private static final int BLOCK_SIZE = 1024;
    private static final int GZIP_HEADER_LENGTH = 10;
    private static final int GZIP_TRAILER_LENGTH = 8;

    private ExecutorService executor;

    @BeforeEach
    void before() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void after() {
        executor.shutdownNow();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, BLOCK_SIZE - 1, BLOCK_SIZE, BLOCK_SIZE + 1, 3 * BLOCK_SIZE, 37 * BLOCK_SIZE + 17})
    void round_trips_through_a_standard_gzip_reader(int length) throws IOException {
        byte[] input = mixedContent(length);

        byte[] compressed = compress(input);

        try (InputStream gunzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            assertThat(gunzip.readAllBytes()).isEqualTo(input);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3 * BLOCK_SIZE + 5})
    void writes_a_single_gzip_member_containing_one_deflate_stream(int length) throws IOException, DataFormatException {
        byte[] input = mixedContent(length);
        byte[] compressed = compress(input);

        // A single member means one deflate stream spans everything between the header and the trailer.
        Inflater inflater = new Inflater(/* nowrap= */ true);
        int deflateLength = compressed.length - GZIP_HEADER_LENGTH - GZIP_TRAILER_LENGTH;
        inflater.setInput(compressed, GZIP_HEADER_LENGTH, deflateLength);
        byte[] output = new byte[length + 1];
        int inflated = inflater.inflate(output);

        assertThat(inflater.finished()).isTrue();
        assertThat(inflater.getRemaining()).isZero();
        assertThat(inflated).isEqualTo(length);
        inflater.end();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 8})
    void output_does_not_depend_on_how_many_blocks_are_in_flight(int maxBlocksInFlight) throws IOException {
        byte[] input = mixedContent(20 * BLOCK_SIZE + 3);

        assertThat(compress(input, maxBlocksInFlight)).isEqualTo(compress(input, 1));
    }

    private byte[] compress(byte[] input) throws IOException {
        return compress(input, 4);
    }

    private byte[] compress(byte[] input, int maxBlocksInFlight) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (ParallelGzipOutputStream gzip =
                new ParallelGzipOutputStream(compressed, executor, maxBlocksInFlight, BLOCK_SIZE)) {
            // Uneven write sizes exercise writes that straddle block boundaries
            int offset = 0;
            int chunk = 1;
            while (offset < input.length) {
                int length = Math.min(chunk, input.length - offset);
                gzip.write(input, offset, length);
                offset += length;
                chunk = chunk * 3 + 1;
            }
        }
        return compressed.toByteArray();
    }

    /** Half incompressible random bytes, half highly compressible text, so both deflate paths are exercised. */
    private static byte[] mixedContent(int length) {
        byte[] bytes = new byte[length];
        Random random = new Random(length);
        for (int i = 0; i < length; i++) {
            bytes[i] = (i / 512) % 2 == 0 ? (byte) random.nextInt() : (byte) ('a' + (i % 7));
        }
        return bytes;
    }
}
