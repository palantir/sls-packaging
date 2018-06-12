/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.gradle.dist.pod.tasks

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.palantir.gradle.dist.pod.PodDistributionPlugin
import com.palantir.gradle.dist.pod.PodServiceDefinition
import com.palantir.gradle.dist.pod.PodVolumeDefinition
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class CreatePodYAMLTask extends DefaultTask {
    private final static String VOLUME_NAME_REGEX = "^(?:[a-z0-9]+?-)*[a-z0-9]+\$"
    public final static String SERVICE_VALIDATION_FAIL_FORMAT = "Pod validation failed for service %s: %s"
    public final static String VOLUME_VALIDATION_FAIL_FORMAT = "Pod validation failed for volume %s: %s"

    public static ObjectMapper jsonMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)

    CreatePodYAMLTask() {
        group = PodDistributionPlugin.GROUP_NAME
        description = "Generates a simple yaml file describing a pods constituent services."
    }

    @Input
    Map<String, PodServiceDefinition> serviceDefinitions

    @Input
    Map<String, PodVolumeDefinition> volumeDefinitions

    @OutputFile
    File getPodYAMLFile() {
        return new File("${project.buildDir}/deployment/pod.yml")
    }

    @TaskAction
    void createPodYAML() {
        validatePodYAML()
        getPodYAMLFile().setText(JsonOutput.prettyPrint(JsonOutput.toJson([
                'services': jsonMapper.convertValue(this.serviceDefinitions, new TypeReference<Map<String, Object>>() {}),
                'volumes': jsonMapper.convertValue(this.volumeDefinitions, new TypeReference<Map<String, Object>>() {}),
        ])))
    }


    void validatePodYAML() {
        def kebabCaseStrategy = new PropertyNamingStrategy.KebabCaseStrategy()
        serviceDefinitions.each { entry ->
            if (!kebabCaseStrategy.translate(entry.key).equals(entry.key)) {
                throw new GradleException(String.format(SERVICE_VALIDATION_FAIL_FORMAT, entry.key,
                        "service names must be kebab case"))
            }

            try {
                entry.value.isValid()
            } catch (IllegalArgumentException e) {
                throw new GradleException(String.format(SERVICE_VALIDATION_FAIL_FORMAT, entry.key, e.message))
            }

            entry.value.volumeMap.each { volume ->
                if (!volumeDefinitions.containsKey(volume.value)) {
                    throw new GradleException(String.format(SERVICE_VALIDATION_FAIL_FORMAT, entry.key,
                            "service volume mapping cannot contain undeclared volumes"))
                }
            }
        }

        volumeDefinitions.each { entry ->
            if ((entry.key.length() >= 25)) {
                throw new GradleException(String.format(VOLUME_VALIDATION_FAIL_FORMAT, entry.key,
                        "volume names must be fewer than 25 characters"))
            }

            if (!entry.key.matches(VOLUME_NAME_REGEX)) {
                throw new GradleException(String.format(VOLUME_VALIDATION_FAIL_FORMAT, entry.key,
                        "volume name does not conform to the required regex ${VOLUME_NAME_REGEX}"))
            }

            if (!entry.value.isValidPodVolumeDefinition()) {
                throw new GradleException(String.format(VOLUME_VALIDATION_FAIL_FORMAT, entry.key,
                        "volume desired size of ${entry.value.desiredSize} does not conform to the required regex ${PodVolumeDefinition.VOLUME_SIZE_REGEX}"))
            }
        }
    }

    void configure(
            Map<String, PodServiceDefinition> services,
            Map<String, PodVolumeDefinition> volumes
    ) {
        this.serviceDefinitions = services
        this.volumeDefinitions = volumes
    }
}
