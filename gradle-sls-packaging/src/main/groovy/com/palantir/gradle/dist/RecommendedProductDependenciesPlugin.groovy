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

package com.palantir.gradle.dist

import com.palantir.gradle.dist.tasks.CreateManifestTask
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar

@CompileStatic
class RecommendedProductDependenciesPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply('java')
        RecommendedProductDependenciesExtension ext = project.extensions.create(
                'recommendedProductDependencies', RecommendedProductDependenciesExtension)

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
