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

import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin;
import com.palantir.gradle.dist.service.util.EmitFiles;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;

/**
 * Generates the {@code java-launcher} and {@code java-init} scripts which replace the go-java-launcher and go-init
 * binaries when a java-based launcher is configured.
 * <p>
 * The main classes are read from the {@value #LAUNCHER_MAIN_CLASS_ATTRIBUTE} and {@value #INIT_MAIN_CLASS_ATTRIBUTE}
 * manifest attributes of the launcher jar.
 */
@AutoParallelizable
public final class CreateJavaLauncherScripts {
    static final String LAUNCHER_SCRIPT = "java-launcher";
    static final String INIT_SCRIPT = "java-init";
    static final String LAUNCHER_MAIN_CLASS_ATTRIBUTE = "Sls-Launcher-Main-Class";
    static final String INIT_MAIN_CLASS_ATTRIBUTE = "Sls-Init-Main-Class";

    interface Params {
        @Input
        Property<String> getJavaHome();

        /** Whether the jdk for the service's java version is bundled in the distribution. */
        @Input
        Property<Boolean> getBundledJdk();

        /** The launcher jars and their runtime dependencies. */
        @Classpath
        ConfigurableFileCollection getClasspath();

        /** The {@code java-launcher} and {@code java-init} scripts, packaged into {@code service/bin}. */
        @OutputDirectory
        DirectoryProperty getOutputDir();

        /**
         * The command the {@code java-launcher} script runs, one argument per line, for consumers starting the
         * launcher without a shell (e.g. as a container's command). Not packaged.
         */
        @OutputFile
        RegularFileProperty getLauncherCommandFile();
    }

    static void action(Params params) {
        Set<File> jars = params.getClasspath().getFiles();
        if (jars.isEmpty()) {
            throw new GradleException("Java-based launcher has been configured, but no jars were resolved for it."
                    + " Configure a launcher with the javaLauncher configuration.");
        }

        if (!params.getBundledJdk().get()) {
            throw new GradleException(
                    "Jdks must be configured and included in the distribution to configure a java-based launcher.");
        }

        // equivalent to the go-java-launcher binary
        String launcherMainClass = mainClass(jars, LAUNCHER_MAIN_CLASS_ATTRIBUTE, "launcher main class");
        // equivalent to the go-init binary
        String initMainClass = mainClass(jars, INIT_MAIN_CLASS_ATTRIBUTE, "init main class");
        List<String> classpath =
                jars.stream().map(jar -> "service/lib/" + jar.getName()).toList();

        File outputDir = params.getOutputDir().get().getAsFile();
        String javaHome = params.getJavaHome().get();
        writeScript(new File(outputDir, LAUNCHER_SCRIPT), javaHome, classpath, launcherMainClass);
        writeScript(new File(outputDir, INIT_SCRIPT), javaHome, classpath, initMainClass);
        writeCommand(
                params.getLauncherCommandFile().get().getAsFile(),
                List.of(javaHome + "/bin/java", "-cp", String.join(":", classpath), launcherMainClass));
    }

    /** The main class declared by the launcher jar's manifest. */
    private static String mainClass(Collection<File> jars, String attribute, String description) {
        Set<String> declared = jars.stream()
                .map(jar -> manifestAttribute(jar, attribute))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (declared.isEmpty()) {
            throw new GradleException("The java-based launcher must declare its " + description + " with the '"
                    + attribute + "' manifest attribute");
        }
        if (declared.size() > 1) {
            throw new GradleException("Multiple jars of the java-based launcher declare the '" + attribute
                    + "' manifest attribute: " + declared);
        }
        String mainClass = declared.iterator().next();
        if (!SourceVersion.isName(mainClass)) {
            throw new GradleException(
                    "The '" + attribute + "' manifest attribute must be a valid class name: " + mainClass);
        }
        return mainClass;
    }

    private static String manifestAttribute(File jar, String attribute) {
        if (!jar.isFile()) {
            return null;
        }
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            return manifest == null ? null : manifest.getMainAttributes().getValue(attribute);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the manifest of " + jar, e);
        }
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

    private static void writeCommand(File file, List<String> command) {
        try {
            Files.createDirectories(file.toPath().getParent());
            Files.write(file.toPath(), command, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }

    private CreateJavaLauncherScripts() {}
}
