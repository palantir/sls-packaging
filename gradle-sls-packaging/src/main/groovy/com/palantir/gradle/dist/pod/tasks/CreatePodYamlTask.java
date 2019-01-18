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

package com.palantir.gradle.dist.pod.tasks;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.dist.pod.PodServiceDefinition;
import com.palantir.gradle.dist.pod.PodVolumeDefinition;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class CreatePodYamlTask extends DefaultTask {
    private static final String VOLUME_NAME_REGEX = "^(?:[a-z0-9]+?-)*[a-z0-9]+$";
    private static final String SERVICE_VALIDATION_FAIL_FORMAT = "Pod validation failed for service %s: %s";
    private static final String VOLUME_VALIDATION_FAIL_FORMAT = "Pod validation failed for volume %s: %s";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT);

    // TODO(forozco): Use MapProperty once our minimum supported version is 5.1
    private Map<String, PodServiceDefinition> serviceDefinitions;
    private Map<String, PodVolumeDefinition> volumeDefinitions;
    private final RegularFileProperty podYamlFile = getProject().getObjects().fileProperty();

    public CreatePodYamlTask() {
        podYamlFile.set(new File(getProject().getBuildDir(), "deployment/pod.yml"));
    }

    @Input
    public final Map<String, PodServiceDefinition> getServiceDefinitions() {
        return serviceDefinitions;
    }

    public final void setServiceDefinitions(Map<String, PodServiceDefinition> serviceDefinitions) {
        this.serviceDefinitions = serviceDefinitions;
    }

    @Input
    public final Map<String, PodVolumeDefinition> getVolumeDefinitions() {
        return volumeDefinitions;
    }

    public final void setVolumeDefinitions(Map<String, PodVolumeDefinition> volumeDefinitions) {
        this.volumeDefinitions = volumeDefinitions;
    }

    @OutputFile
    public final RegularFileProperty getPodYamlFile() {
        return podYamlFile;
    }

    @TaskAction
    final void createPodYaml() throws IOException {
        validatePodYaml();
        OBJECT_MAPPER.writeValue(getPodYamlFile().getAsFile().get(), ImmutableMap.of(
                "services",
                OBJECT_MAPPER.convertValue(this.serviceDefinitions, new TypeReference<Map<String, Object>>() {}),
                "volumes",
                OBJECT_MAPPER.convertValue(this.volumeDefinitions, new TypeReference<Map<String, Object>>() {})));

    }

    private void validatePodYaml() {
        PropertyNamingStrategy.KebabCaseStrategy kebabCaseStrategy = new PropertyNamingStrategy.KebabCaseStrategy();
        serviceDefinitions.forEach((key, value) -> {
            if (!kebabCaseStrategy.translate(key).equals(key)) {
                throw new GradleException(String.format(
                        SERVICE_VALIDATION_FAIL_FORMAT, key, "service names must be kebab case"));
            }

            try {
                value.isValid();
            } catch (IllegalArgumentException e) {
                throw new GradleException(String.format(SERVICE_VALIDATION_FAIL_FORMAT, key, e.getMessage()));
            }

            value.getVolumeMap().forEach((volumeKey, volumeValue) -> {
                if (!volumeDefinitions.containsKey(volumeValue)) {
                    throw new GradleException(String.format(SERVICE_VALIDATION_FAIL_FORMAT, key,
                            "service volume mapping cannot contain undeclared volumes"));
                }
            });
        });

        volumeDefinitions.forEach((key, value) -> {
            if (key.length() >= 25) {
                throw new GradleException(String.format(VOLUME_VALIDATION_FAIL_FORMAT, key,
                        "volume names must be fewer than 25 characters"));
            }

            if (!key.matches(VOLUME_NAME_REGEX)) {
                throw new GradleException(String.format(VOLUME_VALIDATION_FAIL_FORMAT, key,
                        "volume name does not conform to the required regex ${VOLUME_NAME_REGEX}"));
            }

            if (!value.isValidPodVolumeDefinition()) {
                throw new GradleException(String.format(VOLUME_VALIDATION_FAIL_FORMAT, key,
                        String.format("volume desired size of %s does not conform to the required regex %s",
                                value.getDesiredSize(), PodVolumeDefinition.VOLUME_SIZE_REGEX)));
            }
        });
    }
}
