/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service;

import com.palantir.gradle.dist.tasks.CreateManifestTask;
import com.palantir.gradle.dist.tasks.GetDefaultImageTask;
import java.io.File;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class ArtifactsManifestPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPlugins().withId("com.palantir.sls-docker", _plugin -> {
            project.getTasks()
                    .register(GetDefaultImageTask.GET_DEFAULT_IMAGE_TASK_NAME, GetDefaultImageTask.class, task -> {
                        task.getServiceName().set(project.getName());
                        task.getServiceGroup().set(project.getGroup().toString());
                        task.getDefaultImages()
                                .set(new File(project.getBuildDir(), "/deployment/default-artifact.yml"));
                    });
            project.getTasks()
                    .named(
                            CreateManifestTask.CREATE_MANIFEST_TASK_NAME,
                            task -> task.dependsOn(GetDefaultImageTask.GET_DEFAULT_IMAGE_TASK_NAME));
        });
    }
}
