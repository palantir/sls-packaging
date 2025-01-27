/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class CompileRecommendedProductDependencies extends DefaultTask {

    static final ObjectMapper MAPPER = new ObjectMapper();

    @Input
    abstract SetProperty<ProductDependency> getRecommendedProductDependencies();

    @OutputFile
    abstract RegularFileProperty getOutputFile();

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory();

    @Inject
    public CompileRecommendedProductDependencies(Project project) {
        getOutputDirectory()
                .convention(project.getLayout().getBuildDirectory().dir("product-dependencies"));
        getOutputFile().convention(getOutputDirectory().file(RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY + "/product-dependencies.json"));
    }

    @TaskAction
    final void action() throws IOException {

        MAPPER.writeValue(
                getOutputFile().getAsFile().get(),
                RecommendedProductDependencies.builder()
                        .addAllRecommendedProductDependencies(
                                getRecommendedProductDependencies().get())
                        .build());
    }
}
