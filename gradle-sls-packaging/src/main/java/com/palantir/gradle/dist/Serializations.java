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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.gradle.api.GradleException;

public final class Serializations {
    public static final ObjectMapper jsonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void writeSlsManifest(SlsManifest slsManifest, File outputFile) {
        try {
            Files.createDirectories(outputFile.toPath().getParent());
            jsonMapper.writeValue(outputFile, slsManifest);
        } catch (IOException e) {
            throw new GradleException("Unable to write SlsManifest file", e);
        }
    }

    public static SlsManifest readSlsManifest(File file) {
        try {
            return jsonMapper.readValue(file, SlsManifest.class);
        } catch (IOException e) {
            throw new GradleException("Unable to read SlsManifest: " + file, e);
        }
    }

    public static void writeProductDependencyReport(ProductDependencyReport pdr, File outputFile) {
        try {
            Files.createDirectories(outputFile.toPath().getParent());
            jsonMapper.writeValue(outputFile, pdr);
        } catch (IOException e) {
            throw new GradleException("Unable to write ProductDependencyReport file", e);
        }
    }

    public static ProductDependencyReport readProductDependencyReport(File file) {
        try {
            return jsonMapper.readValue(file, ProductDependencyReport.class);
        } catch (IOException e) {
            throw new GradleException("Unable to read ProductDependencyReport: " + file, e);
        }
    }

    private Serializations() {}
}
