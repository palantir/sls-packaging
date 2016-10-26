/*
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.javadist.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.palantir.gradle.javadist.JavaDistributionPlugin
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

class LaunchConfigTask extends BaseTask {

    LaunchConfigTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Generates launcher-static.yml and launcher-check.yml configurations."
    }

    @EqualsAndHashCode
    @ToString
    public static class StaticLaunchConfig {
        // keep in sync with StaticLaunchConfig struct in go-java-launcher
        String configType = "java"
        int configVersion = 1
        String mainClass
        String javaHome
        List<String> classpath
        List<String> jvmOpts
        List<String> args
        Map<String,String> env
    }

    @TaskAction
    void createConfig() {
        writeConfig(createConfig(distributionExtension().args), "scripts/launcher-static.yml")
        writeConfig(createConfig(distributionExtension().checkArgs), "scripts/launcher-check.yml")
    }

    void writeConfig(StaticLaunchConfig config, String relativePath) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        def outfile = project.buildDir.toPath().resolve(relativePath)
        Files.createDirectories(outfile.parent)
        outfile.withWriter { it ->
            mapper.writeValue(it, config)
        }
    }

    StaticLaunchConfig createConfig(List<String> args) {
        StaticLaunchConfig config = new StaticLaunchConfig()
        config.mainClass = distributionExtension().mainClass
        config.javaHome = distributionExtension().javaHome ?: ""
        config.args = args
        config.classpath = relativizeToServiceLibDirectory(
                project.tasks[JavaPlugin.JAR_TASK_NAME].outputs.files + project.configurations.runtime)
        config.jvmOpts = distributionExtension().defaultJvmOpts
        config.env = distributionExtension().env
        return config
    }

    private static List<String> relativizeToServiceLibDirectory(FileCollection files) {
        def output = []
        files.each { output.add("service/lib/" + it.name) }
        return output
    }
}
