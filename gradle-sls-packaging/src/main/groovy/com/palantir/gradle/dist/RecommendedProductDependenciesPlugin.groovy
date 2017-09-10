package com.palantir.gradle.dist

import com.palantir.gradle.dist.tasks.CreateManifestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar

class RecommendedProductDependenciesPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply('java')
        def ext = project.extensions.create('recommendedProductDependencies', RecommendedProductDependenciesExtension)

        project.afterEvaluate {
            ext.recommendedProductDependencies.each { recommendedProductDependency ->
                def recommendedProductDeps = CreateManifestTask.jsonMapper.writeValueAsString(RecommendedProductDependencies.builder()
                        .recommendedProductDependencies(ext.recommendedProductDependencies)
                        .build())
                Jar jar = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME)
                jar.manifest.attributes((CreateManifestTask.SLS_RECOMMENDED_PRODUCT_DEPS_KEY): recommendedProductDeps)
            }
        }
    }

}
