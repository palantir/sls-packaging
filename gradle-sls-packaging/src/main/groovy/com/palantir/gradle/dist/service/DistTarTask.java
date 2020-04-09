/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service;

import java.util.concurrent.Callable;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Tar;

public final class DistTarTask {
    public static void configure(
            Project project,
            Tar distTarTask,
            JavaServiceDistributionExtension distributionExtension,
            TaskProvider<Jar> jarTask) {
        Provider<String> serviceName = distributionExtension.getDistributionServiceName();
        distTarTask.getArchiveBaseName().set(serviceName);

        Callable<String> archiveRootDir = () -> serviceName.get() + "-" + project.getVersion();

        distTarTask.into(archiveRootDir, root -> {
            root.from("var", t -> {
                t.into("var");
                distributionExtension.getExcludeFromVar().get().forEach(t::exclude);
            });

            root.from("service", t -> {
                t.into("service");
                t.exclude("bin/*");
            });

            root.from("service/bin", t -> {
                t.into("service/bin");
                t.setFileMode(0755);
            });

            root.into("service/lib", t -> {
                t.from(jarTask);
                t.from(project.getConfigurations().named("runtimeClasspath"));
            });

            if (distributionExtension.getEnableManifestClasspath().get()) {
                root.into("service/lib", t -> {
                    t.from(project.getTasks().named("manifestClasspathJar"));
                });
            }

            root.into("service/bin", t -> {
                t.from(project.getLayout().getBuildDirectory().dir("scripts"));
                t.setFileMode(0755);
            });

            root.into("service/monitoring/bin", t -> {
                t.from(project.getLayout().getBuildDirectory().dir("monitoring"));
                t.setFileMode(0755);
            });

            root.into("service/lib/linux-x86-64", t -> {
                t.from(project.getLayout().getBuildDirectory().dir("libs/linux-x86-64"));
                t.setFileMode(0755);
            });

            root.into("deployment", t -> {
                t.from("deployment");
                t.from(project.getLayout().getBuildDirectory().dir("deployment"));
                t.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
            });
        });
    }

    private DistTarTask() {}
}
