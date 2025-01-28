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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public abstract class CompileRecommendedProductDependencies extends DefaultTask {


    public static final String RESOURCE_PATH =
            RecommendedProductDependencies.SLS_RECOMMENDED_PRODUCT_DEPS_KEY + "/product-dependencies.json";
    static final ObjectMapper MAPPER = new ObjectMapper();

    @Input
    abstract SetProperty<ProductDependency> getRecommendedProductDependencies();

    /**
     * Ensure that the sourcesJar task in {@link RecommendedProductDependenciesPlugin} includes {@code getProductDependenciesFile()}.
     */
    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory();


    @Internal
    public final File getProductDependenciesFile() {
        return getOutputDirectory().file(RESOURCE_PATH).get().getAsFile();
    }

    @TaskAction
    final void action() throws IOException {
        Files.createDirectories(getProductDependenciesFile().toPath().getParent());
        MAPPER.writeValue(
                getProductDependenciesFile(),
                RecommendedProductDependencies.builder()
                        .addAllRecommendedProductDependencies(
                                getRecommendedProductDependencies().get())
                        .build());
    }
}
