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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import com.palantir.gradle.dist.service.util.EmitFiles;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
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

        @OutputFile
        RegularFileProperty getOutputFile();

        @Input
        @Optional
        Property<String> getJavaHome();

        /** Main class equivalent to the go-java-launcher binary, when a jvm based launcher is used. */
        @Input
        @Optional
        Property<String> getLauncherMainClass();

        /** Main class equivalent to the go-init binary, when a jvm based launcher is used. */
        @Input
        @Optional
        Property<String> getInitMainClass();

        /** Distribution relative paths of the jvm based launcher jars. */
        @InputFiles
        ConfigurableFileCollection getLauncherClasspath();
    }

    static void action(Params params) {
        EmitFiles.replaceVars(
                        resource(INIT_TEMPLATE),
                        params.getOutputFile().get().getAsFile().toPath(),
                        ImmutableMap.of(
                                "@serviceName@",
                                params.getServiceName().get(),
                                "@launcherSetup@",
                                launcherSetup(params)))
                .toFile()
                .setExecutable(true);
    }

    private static String launcherSetup(Params params) {
        if (!params.getLauncherMainClass().isPresent()
                && !params.getInitMainClass().isPresent()) {
            return EmitFiles.replaceVars(resource(GO_LAUNCHER_TEMPLATE), ImmutableMap.of());
        }

        String launcherMainClass = params.getLauncherMainClass().getOrNull();
        String initMainClass = params.getInitMainClass().getOrNull();
        List<String> classpath = relativizeToServiceLibDirectory(params.getLauncherClasspath());
        String javaHome = params.getJavaHome().getOrNull();

        if (Strings.isNullOrEmpty(launcherMainClass)) {
            throw new GradleException(
                    "'distribution.javaLauncher.launcherMainClass' must be specified when a java-based launcher is"
                            + " configured");
        }

        if (Strings.isNullOrEmpty(initMainClass)) {
            throw new GradleException(
                    "'distribution.javaLauncher.initMainClass' must be specified when a java-based launcher is"
                            + " configured");
        }

        if (classpath.isEmpty()) {
            throw new GradleException(
                    "Java-based launcher has been configured, but no jars were resolved for it. Configure a launcher"
                            + " with the " + JavaServiceDistributionPlugin.JAVA_LAUNCHER_CONFIGURATION
                            + " configuration.");
        }

        if (Strings.isNullOrEmpty(javaHome)) {
            throw new GradleException(
                    "Java-based launcher has been configured, but no jdk has been bundled. Make sure jdks are bundled"
                            + " with the distribution.");
        }

        if (javaHome.startsWith("$")) {
            throw new GradleException(
                    "javaHome for java-based launcher must point to a path on disk. Make sure jdks are bundled with"
                            + " the distribution.");
        }

        return EmitFiles.replaceVars(
                resource(JAVA_LAUNCHER_TEMPLATE),
                ImmutableMap.of(
                        "@javaHome@",
                        javaHome,
                        "@launcherClasspath@",
                        String.join(":", classpath),
                        "@launcherMainClass@",
                        launcherMainClass,
                        "@initMainClass@",
                        initMainClass));
    }

    private static List<String> relativizeToServiceLibDirectory(FileCollection files) {
        return files.getFiles().stream()
                .map(file -> "service/lib/" + file.getName())
                .collect(Collectors.toList());
    }

    private static InputStream resource(String resource) {
        return JavaServiceDistributionPlugin.class.getResourceAsStream(resource);
    }

    private CreateInitScript() {}
}
