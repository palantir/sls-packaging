/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
import java.io.File;
import java.util.List;
import javax.lang.model.SourceVersion;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;

/**
 * Generates the {@code java-launcher} and {@code java-init} scripts which replace the go-java-launcher and go-init
 * binaries when a java-based launcher is configured.
 */
@AutoParallelizable
public final class CreateJavaLauncherScripts {
    static final String LAUNCHER_SCRIPT = "java-launcher";
    static final String INIT_SCRIPT = "java-init";

    interface Params {
        /** Main class equivalent to the go-java-launcher binary. */
        @Input
        @Optional
        Property<String> getLauncherMainClass();

        /** Main class equivalent to the go-init binary. */
        @Input
        @Optional
        Property<String> getInitMainClass();

        @Input
        Property<String> getJavaHome();

        /** Whether the jdk for the service's java version is bundled in the distribution. */
        @Input
        Property<Boolean> getBundledJdk();

        /** Distribution relative paths of the launcher jars. */
        @Input
        ListProperty<String> getClasspath();

        @OutputDirectory
        DirectoryProperty getOutputDir();
    }

    static void action(Params params) {
        String launcherMainClass = mainClass(params.getLauncherMainClass().getOrNull(), "launcherMainClass");
        String initMainClass = mainClass(params.getInitMainClass().getOrNull(), "initMainClass");
        List<String> classpath = params.getClasspath().get();

        if (!params.getBundledJdk().get()) {
            throw new GradleException(
                    "Jdks must be configured and included in the distribution to configure a java-based launcher.");
        }

        if (classpath.isEmpty()) {
            throw new GradleException("Java-based launcher has been configured, but no jars were resolved for it."
                    + " Configure a launcher with the " + JavaServiceDistributionPlugin.JAVA_LAUNCHER_CONFIGURATION
                    + " configuration.");
        }

        File outputDir = params.getOutputDir().get().getAsFile();
        String javaHome = params.getJavaHome().get();
        writeScript(new File(outputDir, LAUNCHER_SCRIPT), javaHome, classpath, launcherMainClass);
        writeScript(new File(outputDir, INIT_SCRIPT), javaHome, classpath, initMainClass);
    }

    private static String mainClass(String mainClass, String property) {
        if (Strings.isNullOrEmpty(mainClass)) {
            throw new GradleException("'distribution.javaLauncher." + property
                    + "' must be specified when a java-based launcher is configured");
        }
        if (!SourceVersion.isName(mainClass)) {
            throw new GradleException(
                    "'distribution.javaLauncher." + property + "' must be a valid class name: " + mainClass);
        }
        return mainClass;
    }

    private static void writeScript(File script, String javaHome, List<String> classpath, String mainClass) {
        EmitFiles.replaceVars(
                        JavaServiceDistributionPlugin.class.getResourceAsStream("/sls-packaging/java-launcher.sh"),
                        script.toPath(),
                        ImmutableMap.of(
                                "@javaHome@", javaHome,
                                "@classpath@", String.join(":", classpath),
                                "@mainClass@", mainClass))
                .toFile()
                .setExecutable(true);
    }

    private CreateJavaLauncherScripts() {}
}
