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

package com.palantir.gradle.dist.service.tasks;

import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import com.palantir.gradle.dist.service.util.EmitFiles;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;

@AutoParallelizable
public final class CreateInitScript {
    public static final String DEFAULT_TEMPLATE_RESOURCE = "/sls-packaging/init.sh";

    interface Params {
        @Input
        Property<String> getServiceName();

        @Input
        Property<String> getTemplate();

        @Input
        MapProperty<String, String> getTemplateVars();

        @OutputFile
        RegularFileProperty getOutputFile();
    }

    /** The init script shipped with this plugin, which drives go-init and go-java-launcher. */
    public static String defaultTemplate() {
        return EmitFiles.readTemplate(
                JavaServiceDistributionPlugin.class.getResourceAsStream(DEFAULT_TEMPLATE_RESOURCE));
    }

    static void action(Params params) {
        Map<String, String> vars = new LinkedHashMap<>(
                ImmutableMap.of("@serviceName@", params.getServiceName().get()));
        vars.putAll(params.getTemplateVars().get());

        EmitFiles.replaceVars(
                        params.getTemplate().get(),
                        params.getOutputFile().get().getAsFile().toPath(),
                        vars)
                .toFile()
                .setExecutable(true);
    }

    private CreateInitScript() {}
}
