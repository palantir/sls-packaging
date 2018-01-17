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

package com.palantir.gradle.dist.pod.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.gradle.dist.pod.PodDistributionPlugin
import com.palantir.gradle.dist.pod.PodServiceDefinition
import com.palantir.gradle.dist.pod.PodVolumeDefinition
import com.palantir.gradle.dist.tasks.KebabCaseStrategy
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.GradleException

class CreatePodYAMLTask extends DefaultTask {
    public final static String POD_VALIDATION_FAIL_FORMAT = "Pod validation failed for service %s: %s"

    public static ObjectMapper jsonMapper = new ObjectMapper()
            .setPropertyNamingStrategy(new KebabCaseStrategy())

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
                'services': jsonMapper.convertValue(this.serviceDefinitions, Map),
                'volumes': jsonMapper.convertValue(this.volumeDefinitions, Map),
        ])))
    }


    void validatePodYAML() {
        def kebabCaseStrategy = new KebabCaseStrategy()
        serviceDefinitions.each { entry ->
            if (!kebabCaseStrategy.translate(entry.key).equals(entry.key)) {
                throw new GradleException(String.format(POD_VALIDATION_FAIL_FORMAT, entry.key,
                        "service names must be kebab case"))
            }

            try {
                entry.value.isValid()
            } catch (IllegalArgumentException e) {
                throw new GradleException(String.format(POD_VALIDATION_FAIL_FORMAT, entry.key, e.message))
            }

            entry.value.volumeMap.each { volume ->
                if (!volumeDefinitions.containsKey(volume.value)) {
                    throw new GradleException(String.format(POD_VALIDATION_FAIL_FORMAT, entry.key,
                            "service volume mapping cannot contain undeclared volumes"))
                }
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
