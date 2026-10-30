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
import java.util.Set;
import javax.lang.model.SourceVersion;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
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
        /** Whether the jdk for the service's java version is bundled in the distribution. */
        @Input
        Property<Boolean> getBundledJdk();

        @Input
        Property<String> getJavaHome();

        @Input
        ListProperty<String> getJvmArgs();

        /**
         * The launcher jars and their runtime dependencies. They are expected to be located under {@code service/lib}.
         */
        @Classpath
        ConfigurableFileCollection getClasspath();

        /** Main class equivalent to the go-java-launcher binary. */
        @Input
        Property<String> getLauncherMainClass();

        /** Main class equivalent to the go-init binary. */
        @Input
        Property<String> getInitMainClass();

        @OutputDirectory
        DirectoryProperty getOutputDir();
    }

    static void action(Params params) {
        if (!params.getBundledJdk().get()) {
            throw new GradleException(
                    "Jdks must be configured and included in the distribution to configure a java-based launcher.");
        }

        if (params.getClasspath().isEmpty()) {
            throw new GradleException("The java-based launcher is enabled, but no jars were resolved for it."
                    + " Check 'distribution.javaLauncher.launcher'.");
        }

        String launcherMainClass = mainClass(params.getLauncherMainClass().getOrNull(), "launcherMainClass");
        String initMainClass = mainClass(params.getInitMainClass().getOrNull(), "initMainClass");
        List<String> jvmArgs = params.getJvmArgs().getOrElse(List.of());
        Set<File> jars = params.getClasspath().getFiles();

        // The classpath is bundled in DistTar under service/lib, so we rebuild the classpath using this information
        List<String> classpath =
                jars.stream().map(jar -> "service/lib/" + jar.getName()).toList();

        File outputDir = params.getOutputDir().get().getAsFile();
        String javaHome = params.getJavaHome().get();
        writeScript(new File(outputDir, LAUNCHER_SCRIPT), javaHome, jvmArgs, classpath, launcherMainClass);
        writeScript(new File(outputDir, INIT_SCRIPT), javaHome, jvmArgs, classpath, initMainClass);
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

    private static void writeScript(
            File script, String javaHome, List<String> jvmArgs, List<String> classpath, String mainClass) {
        EmitFiles.replaceVars(
                        JavaServiceDistributionPlugin.class.getResourceAsStream("/sls-packaging/java-launcher.sh"),
                        script.toPath(),
                        ImmutableMap.of(
                                "@javaHome@",
                                javaHome,
                                "@jvmArgs@",
                                String.join(" ", jvmArgs),
                                "@classpath@",
                                String.join(":", classpath),
                                "@mainClass@",
                                mainClass))
                .toFile()
                .setExecutable(true);
    }

    private CreateJavaLauncherScripts() {}
}
