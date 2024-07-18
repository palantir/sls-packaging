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

package com.palantir.gradle.dist;

import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.ProjectLayout;

public final class DeploymentDirInclusion {

    public static void includeFromDeploymentDirs(
            ProjectLayout projectLayout,
            BaseDistributionExtension distributionExtension,
            CopySpec root,
            Action<CopySpec> extraConfig) {

        root.into("deployment", t -> {
            // We exclude configuration.yml from the general "deployment" importer, as it is special cased and
            // handled separately below.
            t.exclude("configuration.yml");
            t.from("deployment");
            t.from(projectLayout.getBuildDirectory().dir("deployment"));
            extraConfig.execute(t);
        });

        root.into("deployment", t -> {
            // Import configuration.yml from the where it is declared in the extension, allowing tasks to
            // generate it and have dependent tasks (like this distTar) get the correct task deps.
            t.from(distributionExtension.getConfigurationYml().map(file -> {
                // We enforce the file is called configuration.yml. Unfortunately, there is an internal
                // piece of code that deduplicates files in gradle-sls-docker. This deduplication is done
                // using this copyspec. Were we to just call `.rename()` on this copyspec arm (to allow plugin
                // devs to output their generated configuration.ymls to some file not called configuration.yml) this
                // rename happens after the renames in the file deduplication code. Unfortunately, it was very hard
                // to disentangle the file deduplication from using this copyspec and maintain build performance
                // - instead we choose to simply check that the `configuration.yml` is called the right thing
                // so it doesn't need to be renamed here.
                if (file.getAsFile().getName().equals("configuration.yml")) {
                    return file;
                }

                throw new IllegalStateException("The file set to be the value of getConfigurationYml() "
                        + "must be called configuration.yml. Instead, it was called " + file.getAsFile());
            }));

            // This is a bit of hack to "fall back" to a `build/deployment/configuration.yml` if the
            // `deployment/configuration.yml` does not exist. This can happen when there's a templating setup
            // that pre-exists the being able to configuration.yml on the extension and it outputs to
            // `build/deployment/configuration.yml`.
            t.from(projectLayout.getBuildDirectory().file("deployment/configuration.yml"));
        });
    }

    private DeploymentDirInclusion() {}
}
