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
package com.palantir.gradle.dist.pod;

import com.palantir.gradle.dist.asset.AssetDistributionPlugin;
import com.palantir.gradle.dist.pod.tasks.CreatePodYamlTask;
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import com.palantir.gradle.dist.tasks.ConfigTarTask;
import com.palantir.gradle.dist.tasks.CreateManifestTask;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Tar;

public final class PodDistributionPlugin implements Plugin<Project> {
    private static final String GROUP_NAME = "Distribution";
    private static final String SLS_CONFIGURATION_NAME = "sls";

    @Override
    public void apply(Project project) {
        if (project.getPlugins().hasPlugin(JavaServiceDistributionPlugin.class)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-pod-distribution' and "
                    + "'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.");
        }
        if (project.getPlugins().hasPlugin(AssetDistributionPlugin.class)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-pod-distribution' and "
                    + "'com.palantir.sls-asset-distribution' cannot be used in the same Gradle project.");
        }
        PodDistributionExtension distributionExtension = project.getExtensions().create(
                "distribution", PodDistributionExtension.class, project);

        distributionExtension.setProductDependenciesConfig(project.getConfigurations().create("podBundle"));

        TaskProvider<CreateManifestTask> manifest = CreateManifestTask.createManifestTask(
                project, distributionExtension);

        TaskProvider<CreatePodYamlTask> podYaml = project.getTasks().register(
                "createPodYaml", CreatePodYamlTask.class, task -> {
                    task.setGroup(PodDistributionPlugin.GROUP_NAME);
                    task.setDescription("Generates a simple yaml file describing a pods constituent services.");
                    task.getServiceDefinitions().set(distributionExtension.getServices());
                    task.getVolumeDefinitions().set(distributionExtension.getVolumes());
                });

        TaskProvider<Tar> configTar = ConfigTarTask.createConfigTarTask(project, distributionExtension);
        configTar.configure(task -> task.dependsOn(manifest, podYaml));

        project.getConfigurations().create(SLS_CONFIGURATION_NAME);
        project.getArtifacts().add(SLS_CONFIGURATION_NAME, configTar);
    }
}
