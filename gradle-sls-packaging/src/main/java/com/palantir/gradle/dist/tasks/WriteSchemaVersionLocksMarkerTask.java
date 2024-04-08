/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;

public class WriteSchemaVersionLocksMarkerTask extends DefaultTask {

    public static final String NAME = "writeSchemaVersionLocks";

    @TaskAction
    public final void checkWriteLocksShouldBeRunning() {
        // Check that our task name matcher for writeSchemaVersionLocks is actually matching up the Gradle one;
        // if this task is running but we didn't actually write locks, error out.
        if (!CreateManifestTask.shouldWriteLocks(getProject(), NAME)) {
            throw new GradleException("This `writeSchemaVersionLocks` marker task has been run, but the "
                    + "schema-versions.lock files did not actually get written out at configuration time. Either "
                    + "there is another task dependency on this task, which is not supported "
                    + "(`writeSchemaVersionLocks` must be run as a gradle task from the command line - not as a "
                    + "dependent task), or this is a bug and should be reported to the owners of the "
                    + "gradle-sls-packaging plugin.");
        }
    }
}
