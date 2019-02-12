/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.palantir.gradle.dist.tasks.CreateManifestTask;
import groovy.lang.Closure;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.util.GFileUtils;

public final class BaseDistributionPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getExtensions().getExtraProperties()
                .set("getMinimumProductVersion", new Closure<String>(project, project) {

                    public String doCall(Object moduleVersion) {
                        List<String> strings = Splitter.on(':').splitToList(moduleVersion.toString());
                        Preconditions.checkState(
                                strings.size() == 2,
                                "Expected 'group:name', found: %s",
                                moduleVersion.toString());

                        return getMinimumProductVersion(project, strings.get(0), strings.get(1));
                    }
                });
    }

    private static String getMinimumProductVersion(Project project, String group, String name) {
        File lockFile = project.file(CreateManifestTask.PRODUCT_DEPENDENCIES_LOCK);
        Preconditions.checkState(Files.exists(lockFile.toPath()),
                "%s does not exist. Run ./gradlew --write-locks to generate it.",
                CreateManifestTask.PRODUCT_DEPENDENCIES_LOCK);

        List<ProductDependency> dependencies = ProductDependencyLockFile.fromString(
                GFileUtils.readFile(lockFile), project.getVersion().toString());

        Optional<ProductDependency> dependency = dependencies.stream()
                .filter(dep -> dep.getProductGroup().equals(group) && dep.getProductName().equals(name))
                .findAny();

        return dependency
                .orElseThrow(() -> new GradleException(
                        String.format("Unable to find product dependency for '%s:%s'", group, name)))
                .getMinimumVersion();
    }
}
