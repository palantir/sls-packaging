/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.pod;

import com.palantir.gradle.dist.BaseDistributionExtension;
import com.palantir.gradle.dist.ProductType;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.util.ConfigureUtil;

public class PodDistributionExtension extends BaseDistributionExtension {
    private MapProperty<String, PodServiceDefinition> services;
    private MapProperty<String, PodVolumeDefinition> volumes;

    @Inject
    public PodDistributionExtension(Project project) {
        super(project);

        services = project.getObjects().mapProperty(String.class, PodServiceDefinition.class);
        volumes = project.getObjects().mapProperty(String.class, PodVolumeDefinition.class);

        setProductType(ProductType.POD_V1);
    }

    public final Provider<Map<String, PodServiceDefinition>> getServices() {
        return services;
    }

    public final void setService(String serviceName, PodServiceDefinition service) {
        this.services.put(serviceName, service);
    }

    public final void setServices(Map<String, PodServiceDefinition> services) {
        this.services.set(services);
    }

    public final void service(String name, @DelegatesTo(PodServiceDefinition.class) Closure closure) {
        PodServiceDefinition service = new PodServiceDefinition();
        ConfigureUtil.configureUsing(closure).execute(service);
        this.services.put(name, service);
    }


    public final Provider<Map<String, PodVolumeDefinition>> getVolumes() {
        return volumes;
    }

    public final void setVolume(String volumeName, PodVolumeDefinition volume) {
        this.volumes.put(volumeName, volume);
    }

    public final void setVolumes(Map<String, PodVolumeDefinition> volumes) {
        this.volumes.set(volumes);
    }

    public final void volume(String name, @DelegatesTo(PodVolumeDefinition.class) Closure closure) {
        PodVolumeDefinition volume = new PodVolumeDefinition();
        ConfigureUtil.configureUsing(closure).execute(volume);
        this.volumes.put(name, volume);
    }
}
