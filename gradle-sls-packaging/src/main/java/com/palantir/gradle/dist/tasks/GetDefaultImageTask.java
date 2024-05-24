/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.gradle.dist.artifacts.ArtifactLocator;
import com.palantir.gradle.dist.service.JavaServiceDistributionExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class GetDefaultImageTask extends DefaultTask {

    public static final String GET_DEFAULT_IMAGE_TASK_NAME = "getDefaultImages";
    public static final String DEFAULT_IMAGE_PREFIX = "docker.external.palantir.build";

    @Input
    public abstract Property<String> getServiceName();

    @Input
    public abstract Property<String> getServiceGroup();

    @Input
    final String getProjectVersion() {
        return getProject().getVersion().toString();
    }

    @OutputFile
    public abstract RegularFileProperty getDefaultImages();

    @TaskAction
    final void getDefaultImage() {
        Project project = getProject();
        project.getExtensions().configure(JavaServiceDistributionExtension.class, ext -> {
            ext.artifact(new ArtifactLocator(
                    "oci",
                    defaultImage(
                            ext.getDistributionServiceGroup().get(),
                            ext.getDistributionServiceName().get(),
                            project.getProject().getVersion().toString())));
        });
    }

    private String defaultImage(String distributionServiceGroup, String distributionServiceName, String version) {
        return DEFAULT_IMAGE_PREFIX + "/" + distributionServiceGroup + "/" + distributionServiceName + ":" + version;
    }
}
