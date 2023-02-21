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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import com.palantir.gradle.dist.service.util.EmitFiles;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;

@AutoParallelizable
public final class CreateCheckScript {
    interface Params {
        @Input
        Property<String> getServiceName();

        @Input
        ListProperty<String> getCheckArgs();

        @OutputFile
        RegularFileProperty getOutputFile();
    }

    static void action(Params params) {
        if (!params.getCheckArgs().get().isEmpty()) {
            EmitFiles.replaceVars(
                            JavaServiceDistributionPlugin.class.getResourceAsStream("/sls-packaging/check.sh"),
                            params.getOutputFile().get().getAsFile().toPath(),
                            ImmutableMap.of(
                                    "@serviceName@", params.getServiceName().get(),
                                    "@checkArgs@",
                                            Joiner.on(" ")
                                                    .join(params.getCheckArgs().get())))
                    .toFile()
                    .setExecutable(true);
        }
    }

    private CreateCheckScript() {}
}
