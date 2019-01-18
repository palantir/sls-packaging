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
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import java.io.File;
import java.io.IOException;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.Tar;

public final class ConfigTarTask {
    private ConfigTarTask() {}

    public static TaskProvider<Tar> createConfigTarTask(Project project, BaseDistributionExtension ext) {
        TaskProvider<Tar> configTar = project.getTasks().register("configTar", Tar.class, task -> {
            task.setGroup(JavaServiceDistributionPlugin.GROUP_NAME);
            task.setDescription(
                    "Creates a compressed, gzipped tar file that contains the sls configuration files for the product");
            task.setCompression(Compression.GZIP);

            task.from(new File(project.getProjectDir(), "deployment"));
            task.from(new File(project.getBuildDir(), "deployment"));
        });

        project.afterEvaluate(p -> configTar.configure(task -> {
            // TODO(forozco): Use provider based API when minimum version is 5.1
            task.setDestinationDir(new File(project.getBuildDir(), "distributions"));
            task.setBaseName(ext.getServiceName().get());
            task.setVersion(project.getVersion().toString());
            task.setExtension(ext.getProductType().map(productType -> {
                try {
                    String productTypeString = CreateManifestTask.jsonMapper.writeValueAsString(productType);
                    return productTypeString.substring(1, productTypeString.lastIndexOf('.')).concat(".config.tgz");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).get());

            // TODO(forozco): make this lazy since into does not support providers, but does support callable
            task.into(String.format("%s-%s/deployment", ext.getServiceName().get(), project.getVersion()));
        }));

        return configTar;
    }
}
