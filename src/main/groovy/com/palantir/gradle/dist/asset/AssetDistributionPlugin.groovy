package com.palantir.gradle.dist.asset

import com.palantir.gradle.dist.asset.tasks.AssetDistTarTask
import com.palantir.gradle.dist.service.JavaDistributionPlugin
import com.palantir.gradle.dist.tasks.CreateManifestTask
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Tar

class AssetDistributionPlugin implements Plugin<Project> {

    static final String GROUP_NAME = "Distribution"
    static final String SLS_CONFIGURATION_NAME = "sls"

    @Override
    void apply(Project project) {
        if (project.getPlugins().hasPlugin(JavaDistributionPlugin)) {
            throw new InvalidUserCodeException("The plugins 'com.palantir.asset-distribution' and 'com.palantir.java-distribution' cannot be used in the same Gradle project.")
        }
        project.extensions.create("distribution", AssetDistributionExtension, project)

        def distributionExtension = project.extensions.findByType(AssetDistributionExtension)

        Task manifest = project.tasks.create('createManifest', CreateManifestTask)
        project.afterEvaluate {
            manifest.configure(
                    distributionExtension.serviceName,
                    distributionExtension.serviceGroup,
                    distributionExtension.productType,
                    distributionExtension.manifestExtensions,
            )
        }

        Tar distTar = AssetDistTarTask.createAssetDistTarTask(project, 'distTar')

        project.afterEvaluate {
            AssetDistTarTask.configure(distTar, distributionExtension.serviceName, distributionExtension.assets)
        }

        project.configurations.create(SLS_CONFIGURATION_NAME)
        project.artifacts.add(SLS_CONFIGURATION_NAME, distTar)

        // Configure tasks
        distTar.dependsOn manifest
    }
}
