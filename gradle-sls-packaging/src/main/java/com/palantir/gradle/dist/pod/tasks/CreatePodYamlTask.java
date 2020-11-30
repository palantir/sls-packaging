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
import java.io.IOException;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
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

    private final MapProperty<String, PodServiceDefinition> serviceDefinitions =
            getProject().getObjects().mapProperty(String.class, PodServiceDefinition.class);
    private final MapProperty<String, PodVolumeDefinition> volumeDefinitions =
            getProject().getObjects().mapProperty(String.class, PodVolumeDefinition.class);
    private final RegularFileProperty podYamlFile = getProject().getObjects().fileProperty();

    public CreatePodYamlTask() {
        podYamlFile.set(getProject().getLayout().getBuildDirectory().file("deployment/pod.yml"));
    }

    @Input
    public final MapProperty<String, PodServiceDefinition> getServiceDefinitions() {
        return serviceDefinitions;
    }

    @Input
    public final MapProperty<String, PodVolumeDefinition> getVolumeDefinitions() {
        return volumeDefinitions;
    }

    @OutputFile
    public final RegularFileProperty getPodYamlFile() {
        return podYamlFile;
    }

    @TaskAction
    final void createPodYaml() throws IOException {
        validatePodYaml();
        OBJECT_MAPPER.writeValue(
                getPodYamlFile().getAsFile().get(),
                ImmutableMap.of(
                        "services",
                        OBJECT_MAPPER.convertValue(
                                this.serviceDefinitions.get(), new TypeReference<Map<String, Object>>() {}),
                        "volumes",
                        OBJECT_MAPPER.convertValue(
                                this.volumeDefinitions.get(), new TypeReference<Map<String, Object>>() {})));
    }

    private void validatePodYaml() {
        PropertyNamingStrategy.KebabCaseStrategy kebabCaseStrategy = new PropertyNamingStrategy.KebabCaseStrategy();
        serviceDefinitions.get().forEach((key, value) -> {
            if (!kebabCaseStrategy.translate(key).equals(key)) {
                throw new GradleException(
                        String.format(SERVICE_VALIDATION_FAIL_FORMAT, key, "service names must be kebab case"));
            }

            try {
                value.isValid();
            } catch (IllegalArgumentException e) {
                throw new GradleException(String.format(SERVICE_VALIDATION_FAIL_FORMAT, key, e.getMessage()));
            }

            value.getVolumeMap().forEach((_volumeKey, volumeValue) -> {
                if (!volumeDefinitions.get().containsKey(volumeValue)) {
                    throw new GradleException(String.format(
                            SERVICE_VALIDATION_FAIL_FORMAT,
                            key,
                            "service volume mapping cannot contain undeclared volumes"));
                }
            });
        });

        volumeDefinitions.get().forEach((key, value) -> {
            if (key.length() >= 25) {
                throw new GradleException(String.format(
                        VOLUME_VALIDATION_FAIL_FORMAT, key, "volume names must be fewer than 25 characters"));
            }

            if (!key.matches(VOLUME_NAME_REGEX)) {
                throw new GradleException(String.format(
                        VOLUME_VALIDATION_FAIL_FORMAT,
                        key,
                        String.format("volume name does not conform to the required regex %s", VOLUME_NAME_REGEX)));
            }

            if (!value.isValidPodVolumeDefinition()) {
                throw new GradleException(String.format(
                        VOLUME_VALIDATION_FAIL_FORMAT,
                        key,
                        String.format(
                                "volume desired size of %s does not conform to the required regex %s",
                                value.getDesiredSize(), PodVolumeDefinition.VOLUME_SIZE_REGEX)));
            }
        });
    }
}
