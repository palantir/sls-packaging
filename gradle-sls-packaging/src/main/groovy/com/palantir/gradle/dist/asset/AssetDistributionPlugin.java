/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.asset;

import com.palantir.gradle.dist.pod.PodDistributionPlugin;
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import com.palantir.gradle.dist.tasks.ConfigTarTask;
import com.palantir.gradle.dist.tasks.CreateManifestTask;
import java.io.File;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Tar;

public final class AssetDistributionPlugin implements Plugin<Project> {
    public static final String GROUP_NAME = "Distribution";
    private static final String SLS_CONFIGURATION_NAME = "sls";

    @Override
    public void apply(Project project) {
        if (project.getPlugins().hasPlugin(JavaServiceDistributionPlugin.class)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-asset-distribution' and "
                    + "'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.");
        }
        if (project.getPlugins().hasPlugin(PodDistributionPlugin.class)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-pod-distribution' and "
                    + "'com.palantir.sls-asset-distribution' cannot be used in the same Gradle project.");
        }

        AssetDistributionExtension distributionExtension = project.getExtensions().create(
                "distribution", AssetDistributionExtension.class, project, project.getObjects());
        distributionExtension.setProductDependenciesConfig(project.getConfigurations().maybeCreate("assetBundle"));

        TaskProvider<CreateManifestTask> manifest = CreateManifestTask.createManifestTask(
                project, distributionExtension);

        TaskProvider<Tar> distTar = project.getTasks().register("distTar", Tar.class, task -> {
            task.setGroup(AssetDistributionPlugin.GROUP_NAME);
            task.setDescription("Creates a compressed, gzipped tar file that contains required static assets.");
            task.setCompression(Compression.GZIP);

            task.getArchiveExtension().set("sls.tgz");
            task.getArchiveBaseName().set(distributionExtension.getServiceName());
            task.getArchiveVersion().set(project.provider(() -> project.getVersion().toString()));
            task.getDestinationDirectory().set(new File(project.getBuildDir(), "distributions"));

            task.from(new File(project.getProjectDir(), "deployment"));
            task.from(new File(project.getBuildDir(), "deployment"));
            task.dependsOn(manifest);
        });

        // HACKHACK after evaluate to configure task with all declared assets
        project.afterEvaluate(p -> distTar.configure(task -> {
            String archiveRootDir = String.format("%s-%s",
                    distributionExtension.getServiceName().get(), p.getVersion());
            task.into(String.format("%s/deployment", archiveRootDir));
            distributionExtension.getAssets().get().forEach((key, value) -> {
                task.from(p.file(key));
                task.into(String.format("%s/asset/%s", archiveRootDir, value));
            });
        }));


        TaskProvider<Tar> configTar = ConfigTarTask.createConfigTarTask(project,  distributionExtension);
        configTar.configure(task -> task.dependsOn(manifest));

        project.getConfigurations().create(SLS_CONFIGURATION_NAME);
        project.getArtifacts().add(SLS_CONFIGURATION_NAME, distTar);
    }
}
