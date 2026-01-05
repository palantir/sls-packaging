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

import org.gradle.api.DefaultTask;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class LaunchConfigTask extends DefaultTask {
    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract Property<String> getServiceName();

    @Input
    public abstract ListProperty<String> getGcJvmOptions();

    @Input
    public abstract Property<Boolean> getAddJava8GcLogging();

    @Input
    @Optional
    public abstract Property<String> getJavaHome();

    @Input
    public abstract Property<JavaVersion> getJavaVersion();

    @Input
    public abstract Property<Boolean> getBundledJdks();

    @Input
    public abstract Property<Boolean> getAlwaysPreTouch();

    @Input
    public abstract ListProperty<String> getArgs();

    @Input
    public abstract ListProperty<String> getCheckArgs();

    @Input
    public abstract ListProperty<String> getDefaultJvmOpts();

    @Input
    public abstract MapProperty<String, String> getEnv();

    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * The difference between fullClasspath and classpath is that classpath is what is written
     * to the launcher-static.yml file, this <i>might</i> in some cases be the manifest classpath
     * JAR. Full Classpath on the other hand is always going to be the full set of JARs which may
     * be the same as classpath if manifest classpath JARs are not used.
     */
    @InputFiles
    public abstract ConfigurableFileCollection getFullClasspath();

    @InputFiles
    public abstract ConfigurableFileCollection getJavaAgents();

    @OutputFile
    public abstract RegularFileProperty getStaticLauncher();

    @OutputFile
    public abstract RegularFileProperty getCheckLauncher();

    public LaunchConfigTask() {
        getStaticLauncher().set(getProject().getLayout().getBuildDirectory().file("scripts/launcher-static.yml"));
        getCheckLauncher().set(getProject().getLayout().getBuildDirectory().file("scripts/launcher-check.yml"));
    }

    @TaskAction
    public final void action() {
        LaunchConfig.action(this);
    }
}
