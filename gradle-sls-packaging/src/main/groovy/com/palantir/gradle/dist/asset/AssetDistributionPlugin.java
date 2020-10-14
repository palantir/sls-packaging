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

import com.palantir.gradle.dist.ProductDependencyIntrospectionPlugin;
import com.palantir.gradle.dist.SlsBaseDistPlugin;
import com.palantir.gradle.dist.pod.PodDistributionPlugin;
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import com.palantir.gradle.dist.tasks.ConfigTarTask;
import com.palantir.gradle.dist.tasks.CreateManifestTask;
import com.palantir.gradle.versions.VersionsLockExtension;
import java.io.File;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Tar;

public final class AssetDistributionPlugin implements Plugin<Project> {
    public static final String GROUP_NAME = "Distribution";
    public static final String ASSET_CONFIGURATION = "assetBundle";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SlsBaseDistPlugin.class);
        if (project.getPlugins().hasPlugin(JavaServiceDistributionPlugin.class)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-asset-distribution' and "
                    + "'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.");
        }
        if (project.getPlugins().hasPlugin(PodDistributionPlugin.class)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-pod-distribution' and "
                    + "'com.palantir.sls-asset-distribution' cannot be used in the same Gradle project.");
        }
        project.getPluginManager().apply(ProductDependencyIntrospectionPlugin.class);

        // If GCV is applied, we want to lock the asset configuration.
        // This is to wrong around a bug where, during --write-locks, the locks from locked configurations don't get
        // exposed to consumers (as constraints) and so non-locked configurations get different versions than when
        // running without `--write-locks`.
        project.getRootProject().getPlugins().withId("com.palantir.consistent-versions", _plugin -> {
            project.getExtensions().configure(VersionsLockExtension.class, lockExt -> {
                lockExt.production(prod -> prod.from(ASSET_CONFIGURATION));
            });
        });

        Configuration assetConfiguration = project.getConfigurations().create(ASSET_CONFIGURATION, conf -> {
            conf.setCanBeConsumed(false);
        });

        AssetDistributionExtension distributionExtension =
                project.getExtensions().create("distribution", AssetDistributionExtension.class, project);
        distributionExtension.setProductDependenciesConfig(assetConfiguration);

        TaskProvider<CreateManifestTask> manifest =
                CreateManifestTask.createManifestTask(project, distributionExtension);

        TaskProvider<Tar> distTar = project.getTasks().register("distTar", Tar.class, task -> {
            task.setGroup(AssetDistributionPlugin.GROUP_NAME);
            task.setDescription("Creates a compressed, gzipped tar file that contains required static assets.");
            task.setCompression(Compression.GZIP);
            task.getArchiveBaseName().set(distributionExtension.getDistributionServiceName());
            task.getArchiveVersion()
                    .set(project.provider(() -> project.getVersion().toString()));
            task.getArchiveExtension().set("sls.tgz");
            task.getDestinationDirectory()
                    .set(project.getLayout().getBuildDirectory().dir("distributions"));
            task.setDuplicatesStrategy(DuplicatesStrategy.FAIL);

            task.dependsOn(manifest);
        });

        // HACKHACK after evaluate to configure task with all declared assets, this is required since
        // task.into doesn't support providers
        project.afterEvaluate(p -> distTar.configure(task -> {
            String archiveRootDir = String.format(
                    "%s-%s", distributionExtension.getDistributionServiceName().get(), p.getVersion());

            task.from(
                    new File(project.getProjectDir(), "deployment"),
                    t -> t.into(new File(String.format("%s/deployment", archiveRootDir))));

            task.from(
                    new File(project.getBuildDir(), "deployment"),
                    t -> t.into(new File(String.format("%s/deployment", archiveRootDir))));

            distributionExtension
                    .getAssets()
                    .get()
                    .forEach((key, value) -> task.from(p.file(key), t -> {
                        t.into(String.format("%s/asset/%s", archiveRootDir, value));
                        // We have tests that ascertain you get the overridden file, make this explicit.
                        t.setDuplicatesStrategy(DuplicatesStrategy.WARN);
                    }));
        }));

        TaskProvider<Tar> configTar = ConfigTarTask.createConfigTarTask(project, distributionExtension);
        configTar.configure(task -> task.dependsOn(manifest));

        project.getArtifacts().add(SlsBaseDistPlugin.SLS_CONFIGURATION_NAME, distTar);
    }
}
