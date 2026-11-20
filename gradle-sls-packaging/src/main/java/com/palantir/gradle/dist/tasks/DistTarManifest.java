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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** The resolved archive entries handed from {@link SlsDistTar} to {@link WriteDistTarWorkAction}, in order. */
final class DistTarManifest {
    private DistTarManifest() {}

    record Entry(boolean isDirectory, int mode, long lastModifiedMillis, String path, String sourcePath) {}

    static void write(DataOutput output, Entry entry) throws IOException {
        output.writeBoolean(entry.isDirectory());
        output.writeInt(entry.mode());
        output.writeLong(entry.lastModifiedMillis());
        output.writeUTF(entry.path());
        output.writeUTF(entry.sourcePath());
    }

    /** Reads the next entry, throwing {@link java.io.EOFException} once the manifest is exhausted. */
    static Entry read(DataInput input) throws IOException {
        return new Entry(input.readBoolean(), input.readInt(), input.readLong(), input.readUTF(), input.readUTF());
    }
}
