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
package com.palantir.gradle.dist.pod

import com.palantir.gradle.dist.BaseDistributionExtension
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

@CompileStatic
class PodDistributionExtension extends BaseDistributionExtension {

    private Map<String, PodServiceDefinition> services = [:]
    private Map<String, PodVolumeDefinition> volumes = [:]

    PodDistributionExtension(Project project) {
        super(project)
        productType("pod.v1")
    }

    Map<String, PodServiceDefinition> getServices() {
        return services
    }

    Map<String, PodVolumeDefinition> getVolumes() {
        return volumes
    }

    void service(String name, Closure closure) {
        PodServiceDefinition service = new PodServiceDefinition()
        ConfigureUtil.configureUsing(closure).execute(service)
        this.services.put(name, service)
    }

    void services(String serviceName, PodServiceDefinition service) {
        this.services.put(serviceName, service)
    }

    void volume(String name, Closure closure) {
        PodVolumeDefinition volume = new PodVolumeDefinition()
        ConfigureUtil.configureUsing(closure).execute(volume)
        this.volumes.put(name, volume)
    }

    void setServices(Map<String, PodServiceDefinition> services) {
        this.services = services
    }

    void setVolumes(Map<String, PodVolumeDefinition> volumes) {
        this.volumes = volumes
    }
}
