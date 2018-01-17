/*
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.dist.pod

import com.palantir.gradle.dist.pod.tasks.CreatePodYAMLTask
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import com.palantir.gradle.dist.asset.AssetDistributionPlugin
import com.palantir.gradle.dist.tasks.CreateManifestTask
import com.palantir.gradle.dist.tasks.ConfigTarTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Tar

class PodDistributionPlugin implements Plugin<Project> {

    static final String GROUP_NAME = "Distribution"
    static final String SLS_CONFIGURATION_NAME = "sls"

    @Override
    void apply(Project project) {
        if (project.getPlugins().hasPlugin(JavaServiceDistributionPlugin)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-pod-distribution' and 'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.")
        }
        if (project.getPlugins().hasPlugin(AssetDistributionPlugin)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.sls-pod-distribution' and 'com.palantir.sls-asset-distribution' cannot be used in the same Gradle project.")
        }
        project.extensions.create("distribution", PodDistributionExtension, project)

        def distributionExtension = project.extensions.findByType(PodDistributionExtension)

        distributionExtension.productDependenciesConfig = project.configurations.create("podBundle")

        CreateManifestTask manifest = project.tasks.create('createManifest', CreateManifestTask)
        project.afterEvaluate {
            manifest.configure(
                    distributionExtension.serviceName,
                    distributionExtension.serviceGroup,
                    distributionExtension.productType,
                    distributionExtension.manifestExtensions,
                    distributionExtension.serviceDependencies,
                    distributionExtension.productDependenciesConfig,
                    distributionExtension.ignoredProductIds)
        }

        CreatePodYAMLTask podYaml = project.tasks.create('createPodYaml', CreatePodYAMLTask)
        project.afterEvaluate {
            podYaml.configure(distributionExtension.services, distributionExtension.volumes)
        }

        Tar configTar = ConfigTarTask.createConfigTarTask(project, 'configTar', distributionExtension.productType)
        project.afterEvaluate {
            ConfigTarTask.configure(configTar, project, distributionExtension.serviceName)
        }

        project.configurations.create(SLS_CONFIGURATION_NAME)
        project.artifacts.add(SLS_CONFIGURATION_NAME, configTar)

        // Configure tasks
        configTar.dependsOn manifest, podYaml
    }
}
