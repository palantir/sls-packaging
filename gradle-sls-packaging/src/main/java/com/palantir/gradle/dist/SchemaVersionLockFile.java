/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableSchemaVersionLockFile.class)
@JsonSerialize(as = ImmutableSchemaVersionLockFile.class)
public interface SchemaVersionLockFile {

    String COMMENT = "Run ./gradlew --write-locks to regenerate this file";
    String LOCK_FILE = "schema-versions.lock";

    @Value.Default
    default String getComment() {
        return COMMENT;
    }

    List<SchemaMigrationRange> getSchemaMigrationRanges();

    static SchemaVersionLockFile of(List<SchemaMigration> schemaMigrations) {
        Preconditions.checkArgument(!schemaMigrations.isEmpty(), "Migrations must not be empty");
        List<SchemaMigration> sortedMigrations =
                schemaMigrations.stream().sorted().collect(Collectors.toList());

        List<SchemaMigrationRange> ranges = new ArrayList<>();
        SchemaMigration firstOfRange = sortedMigrations.get(0);
        SchemaMigration lastOfRange = firstOfRange;
        for (SchemaMigration currentMigration : sortedMigrations.subList(1, sortedMigrations.size())) {
            Preconditions.checkArgument(
                    currentMigration.fromVersion() != lastOfRange.fromVersion(),
                    "Multiple migrations with the same from version are not allowed");
            if (currentMigration.type().equals(lastOfRange.type())
                    && currentMigration.fromVersion() == lastOfRange.fromVersion() + 1) {
                lastOfRange = currentMigration;
            } else {
                ranges.add(SchemaMigrationRange.of(
                        firstOfRange.type(), firstOfRange.fromVersion(), lastOfRange.fromVersion()));
                firstOfRange = currentMigration;
                lastOfRange = currentMigration;
            }
        }
        ranges.add(SchemaMigrationRange.of(firstOfRange.type(), firstOfRange.fromVersion(), lastOfRange.fromVersion()));

        return ImmutableSchemaVersionLockFile.builder()
                .schemaMigrationRanges(ranges)
                .build();
    }
}
