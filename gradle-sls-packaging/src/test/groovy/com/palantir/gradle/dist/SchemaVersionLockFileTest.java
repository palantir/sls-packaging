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

import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SchemaVersionLockFileTest {
    @Test
    void testEmpty() {
        try {
            SchemaVersionLockFile.asString(ImmutableList.of());
            Assertions.fail();
        } catch (SafeIllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testSingle() {
        List<SchemaMigration> migrations = new ArrayList<>();
        migrations.add(SchemaMigration.of("online", 100));
        String str = SchemaVersionLockFile.asString(migrations);
        Assertions.assertEquals(SchemaVersionLockFile.HEADER + "online [100, 100]\n", str);
    }

    @Test
    void testContiguousRange() {
        List<SchemaMigration> migrations = new ArrayList<>();
        for (int i = 100; i < 110; i++) {
            migrations.add(SchemaMigration.of("online", i));
        }
        String str = SchemaVersionLockFile.asString(migrations);
        Assertions.assertEquals(SchemaVersionLockFile.HEADER + "online [100, 109]\n", str);
    }

    @Test
    void testGap() {
        List<SchemaMigration> migrations = new ArrayList<>();
        migrations.add(SchemaMigration.of("online", 100));
        migrations.add(SchemaMigration.of("online", 102));
        String str = SchemaVersionLockFile.asString(migrations);
        Assertions.assertEquals(SchemaVersionLockFile.HEADER + "online [100, 100]\nonline [102, 102]\n", str);
    }

    @Test
    void testMultipleRanges() {
        List<SchemaMigration> migrations = new ArrayList<>();
        migrations.add(SchemaMigration.of("online", 100));
        migrations.add(SchemaMigration.of("online", 101));
        migrations.add(SchemaMigration.of("offline", 102));
        migrations.add(SchemaMigration.of("offline", 103));
        migrations.add(SchemaMigration.of("online", 104));
        migrations.add(SchemaMigration.of("offline", 105));
        String str = SchemaVersionLockFile.asString(migrations);
        Assertions.assertEquals(
                SchemaVersionLockFile.HEADER
                        + "online [100, 101]\noffline [102, 103]\nonline [104, 104]\noffline [105, 105]\n",
                str);
    }
}
