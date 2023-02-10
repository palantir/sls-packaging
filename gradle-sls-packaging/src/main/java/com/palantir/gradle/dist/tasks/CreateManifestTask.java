/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

import org.gradle.api.file.RegularFile;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;

public abstract class CreateManifestTask extends CreateManifestTaskImpl {
    public CreateManifestTask() {
        getShouldWriteLocks().set(getProject().provider(() -> {
            String taskName = project.getPath().equals(":")
                    ? ":writeProductDependenciesLocks"
                    : project.getPath() + ":writeProductDependenciesLocks";
            Gradle gradle = project.getGradle();
            return gradle.getStartParameter().isWriteDependencyLocks()
                    || gradle.getTaskGraph().hasTask(taskName);
        }));
    }

    /**
     * Intentionally checking whether file exists as gradle's {@link org.gradle.api.tasks.Optional} only operates on
     * whether the method returns null or not. Otherwise, it will fail when the file doesn't exist.
     */
    @InputFile
    @org.gradle.api.tasks.Optional
    final Provider<RegularFile> getLockfileIfExists() {
        if (lockfileExists()) {
            return getLockfile();
        }

        return getLockfile().map(_ignored -> null);
    }
}
