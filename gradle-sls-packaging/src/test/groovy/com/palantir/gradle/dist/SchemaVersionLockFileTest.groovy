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

package com.palantir.gradle.dist

import com.palantir.logsafe.exceptions.SafeIllegalArgumentException
import spock.lang.Specification

public class SchemaVersionLockFileTest extends Specification {

    def 'empty is not allowed'() {
        when:
        SchemaVersionLockFile.of([])

        then:
        thrown(SafeIllegalArgumentException)
    }

    void 'works with one migration'() {
        when:
        List<SchemaMigration> migrations = [SchemaMigration.of("online", 100)]
        String str = ObjectMappers.writeSchemaVersionsAsString(SchemaVersionLockFile.of(migrations))

        then:
        str == """\
        ---
        comment: "${SchemaVersionLockFile.COMMENT}"
        schemaMigrationRanges:
        - type: "online"
          firstVersion: 100
          lastVersion: 100
        """.stripIndent()
    }

    void 'works with contiguous range'() {
        when:
        List<SchemaMigration> migrations = [
            SchemaMigration.of("online", 100),
            SchemaMigration.of("online", 101),
            SchemaMigration.of("online", 102),
        ]
        String str = ObjectMappers.writeSchemaVersionsAsString(SchemaVersionLockFile.of(migrations))

        then:
        str == """\
        ---
        comment: "${SchemaVersionLockFile.COMMENT}"
        schemaMigrationRanges:
        - type: "online"
          firstVersion: 100
          lastVersion: 102
        """.stripIndent()
    }

    void 'works with gap'() {
        when:
        List<SchemaMigration> migrations = [
            SchemaMigration.of("online", 100),
            SchemaMigration.of("online", 102),
        ]
        String str = ObjectMappers.writeSchemaVersionsAsString(SchemaVersionLockFile.of(migrations))

        then:
        str == """\
        ---
        comment: "${SchemaVersionLockFile.COMMENT}"
        schemaMigrationRanges:
        - type: "online"
          firstVersion: 100
          lastVersion: 100
        - type: "online"
          firstVersion: 102
          lastVersion: 102
        """.stripIndent()
    }

    void 'works with multiple ranges'() {
        when:
        List<SchemaMigration> migrations = [
            SchemaMigration.of("online", 100),
            SchemaMigration.of("online", 101),
            SchemaMigration.of("offline", 102),
            SchemaMigration.of("offline", 103),
            SchemaMigration.of("online", 104),
            SchemaMigration.of("offline", 105),
        ]
        String str = ObjectMappers.writeSchemaVersionsAsString(SchemaVersionLockFile.of(migrations))

        then:
        str == """\
        ---
        comment: "${SchemaVersionLockFile.COMMENT}"
        schemaMigrationRanges:
        - type: "online"
          firstVersion: 100
          lastVersion: 101
        - type: "offline"
          firstVersion: 102
          lastVersion: 103
        - type: "online"
          firstVersion: 104
          lastVersion: 104
        - type: "offline"
          firstVersion: 105
          lastVersion: 105
        """.stripIndent()
    }

    void 'deserialization works'() {
        when:
        List<SchemaMigration> migrations = [
            SchemaMigration.of("online", 100),
            SchemaMigration.of("online", 101),
            SchemaMigration.of("offline", 102),
            SchemaMigration.of("offline", 103),
            SchemaMigration.of("online", 104),
            SchemaMigration.of("offline", 105),
        ]
        SchemaVersionLockFile expectedLockFile = SchemaVersionLockFile.of(migrations)
        String str = ObjectMappers.writeSchemaVersionsAsString(expectedLockFile)
        SchemaVersionLockFile lockFile = ObjectMappers.readSchemaVersionsFromString(str)

        then:
        lockFile == expectedLockFile
    }
}
