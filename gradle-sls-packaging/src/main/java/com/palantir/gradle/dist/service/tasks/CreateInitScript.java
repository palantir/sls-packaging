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
import java.io.InputStream;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

@AutoParallelizable
public final class CreateInitScript {
    private static final String INIT_TEMPLATE = "/sls-packaging/init.sh";
    private static final String GO_LAUNCHER_TEMPLATE = "/sls-packaging/init-launcher-go.sh";
    private static final String JAVA_LAUNCHER_TEMPLATE = "/sls-packaging/init-launcher-java.sh";

    interface Params {
        @Input
        Property<String> getServiceName();

        /** Main class equivalent to the go-java-launcher binary, when a jvm based launcher is used. */
        @Input
        @Optional
        Property<String> getLauncherMainClass();

        /** Main class equivalent to the go-init binary, when a jvm based launcher is used. */
        @Input
        @Optional
        Property<String> getInitMainClass();

        /** Distribution relative paths of the jvm based launcher jars. */
        @Input
        ListProperty<String> getLauncherClasspath();

        @Input
        @Optional
        Property<String> getJavaHome();

        @OutputFile
        RegularFileProperty getOutputFile();
    }

    static void action(Params params) {
        EmitFiles.replaceVars(
                        template(INIT_TEMPLATE),
                        params.getOutputFile().get().getAsFile().toPath(),
                        ImmutableMap.of(
                                "@serviceName@", params.getServiceName().get(),
                                "@launcherSetup@", launcherSetup(params)))
                .toFile()
                .setExecutable(true);
    }

    private static String launcherSetup(Params params) {
        String launcherMainClass = params.getLauncherMainClass().getOrNull();
        String initMainClass = params.getInitMainClass().getOrNull();
        List<String> launcherClasspath = params.getLauncherClasspath().get();

        if (launcherMainClass == null && initMainClass == null) {
            if (!launcherClasspath.isEmpty()) {
                throw new GradleException("Dependencies have been added to the 'javaLauncherBinary' configuration, but "
                        + "'distribution.javaLauncher.launcherMainClass' and "
                        + "'distribution.javaLauncher.initMainClass' have not been set");
            }
            return trimTrailingNewline(EmitFiles.replaceVars(template(GO_LAUNCHER_TEMPLATE), ImmutableMap.of()));
        }

        if (launcherMainClass == null || initMainClass == null) {
            throw new GradleException("Both 'distribution.javaLauncher.launcherMainClass' and "
                    + "'distribution.javaLauncher.initMainClass' must be set to use a jvm based launcher");
        }

        if (launcherClasspath.isEmpty()) {
            throw new GradleException("A jvm based launcher has been configured, but no jars were resolved for it. "
                    + "Set 'distribution.javaLauncher.coordinate' or add a dependency to the 'javaLauncherBinary' "
                    + "configuration");
        }

        return trimTrailingNewline(EmitFiles.replaceVars(
                template(JAVA_LAUNCHER_TEMPLATE),
                ImmutableMap.of(
                        "@javaHome@",
                        params.getJavaHome().getOrElse(""),
                        "@launcherClasspath@",
                        String.join(":", launcherClasspath),
                        "@launcherMainClass@",
                        launcherMainClass,
                        "@initMainClass@",
                        initMainClass)));
    }

    private static InputStream template(String resource) {
        return JavaServiceDistributionPlugin.class.getResourceAsStream(resource);
    }

    private static String trimTrailingNewline(String text) {
        return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
    }

    private CreateInitScript() {}
}
