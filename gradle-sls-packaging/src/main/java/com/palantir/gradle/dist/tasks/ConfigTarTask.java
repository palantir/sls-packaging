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

import com.palantir.gradle.dist.BaseDistributionExtension;
import com.palantir.gradle.dist.DeploymentDirInclusion;
import com.palantir.gradle.dist.ObjectMappers;
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import java.io.IOException;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Tar;

public abstract class ConfigTarTask extends Tar {
    @Override
    public final AbstractCopyTask from(Object... sourcePaths) {
        return this.from(sourcePaths, _ignored -> {});
    }

    @Override
    public final AbstractCopyTask from(Object sourcePath, Action<? super CopySpec> configureAction) {
        return super.from(sourcePath, copySpec -> {
            // These from methods are overriden so we can set a default `into` on each from copyspec. Originally, the
            // task level `into` was set to `deployment`, which forced all files into the `deployment` directory.
            // However, with launcher-static.yml, we want to keep it at the same path as the dist, in service/bin/,
            // for consistency. However, other plugins (like hyperion) add their own custom `from`s, so to maintain
            // backcompat we override all the froms and set the default per from into to be `deployment`.
            copySpec.into("deployment");
            configureAction.execute(copySpec);
        });
    }

    public static TaskProvider<ConfigTarTask> createConfigTarTask(Project project, BaseDistributionExtension ext) {
        TaskProvider<ConfigTarTask> configTar = project.getTasks().register("configTar", ConfigTarTask.class, task -> {
            task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
            task.setDescription(
                    "Creates a compressed, gzipped tar file that contains the sls configuration files for the product");
            task.setCompression(Compression.GZIP);

            DeploymentDirInclusion.includeFromDeploymentDirs(project.getLayout(), ext, task, _ignored -> {});

            task.getDestinationDirectory()
                    .set(project.getLayout().getBuildDirectory().dir("distributions"));
            task.getArchiveBaseName().set(ext.getDistributionServiceName());
            task.getArchiveVersion()
                    .set(project.provider(() -> project.getVersion().toString()));
            task.getArchiveExtension().set(ext.getProductType().map(productType -> {
                try {
                    String productTypeString = ObjectMappers.jsonMapper.writeValueAsString(productType);
                    return productTypeString
                            .substring(1, productTypeString.lastIndexOf('.'))
                            .concat(".config.tgz");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
        });

        // TODO(forozco): make this lazy since into does not support providers, but does support callable
        project.afterEvaluate(_p -> configTar.configure(task -> {
            task.into(String.format("%s-%s", ext.getDistributionServiceName().get(), project.getVersion()));
        }));

        return configTar;
    }
}
