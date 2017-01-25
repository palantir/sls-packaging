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

package com.palantir.gradle.dist.service.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.palantir.gradle.dist.service.ServiceDistributionPlugin
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*

import java.nio.file.Files

class LaunchConfigTask extends DefaultTask {

    static final List<String> tmpdirJvmOpts = [
            '-Djava.io.tmpdir=var/data/tmp'
    ]

    static final List<String> gcJvmOpts = [
            '-XX:+PrintGCDateStamps',
            '-XX:+PrintGCDetails',
            '-XX:-TraceClassUnloading',
            '-XX:+UseGCLogFileRotation',
            '-XX:GCLogFileSize=10M',
            '-XX:NumberOfGCLogFiles=10',
            '-Xloggc:var/log/gc-%t-%p.log',
            '-verbose:gc'
    ]

    @Input
    String mainClass

    @Input
    List<String> args

    @Input
    List<String> checkArgs

    @Input
    List<String> defaultJvmOpts

    @Input
    Map<String, String> env

    @Input
    @Optional
    String javaHome

    @InputFiles
    FileCollection classpath

    LaunchConfigTask() {
        group = ServiceDistributionPlugin.GROUP_NAME
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
        Map<String, String> env
    }

    @OutputFile
    public File getStaticLauncher() {
        return new File("scripts/launcher-static.yml")
    }

    @OutputFile
    public File getCheckLauncher() {
        return new File("scripts/launcher-check.yml")
    }

    @TaskAction
    void createConfig() {
        writeConfig(createConfig(getArgs(), tmpdirJvmOpts + gcJvmOpts + defaultJvmOpts), getStaticLauncher())
        writeConfig(createConfig(getCheckArgs(), tmpdirJvmOpts + defaultJvmOpts), getCheckLauncher())
    }

    void writeConfig(StaticLaunchConfig config, File scriptFile) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        def outfile = project.buildDir.toPath().resolve(scriptFile.toPath())
        Files.createDirectories(outfile.parent)
        outfile.withWriter { it ->
            mapper.writeValue(it, config)
        }
    }

    StaticLaunchConfig createConfig(List<String> args, List<String> jvmOpts) {
        StaticLaunchConfig config = new StaticLaunchConfig()
        config.mainClass = mainClass
        config.javaHome = javaHome ?: ""
        config.args = args
        config.classpath = relativizeToServiceLibDirectory(classpath)
        config.jvmOpts = jvmOpts
        config.env = env
        return config
    }

    private static List<String> relativizeToServiceLibDirectory(FileCollection files) {
        def output = []
        files.each { output.add("service/lib/" + it.name) }
        return output
    }

    public void configure(String mainClass, List<String> args, List<String> checkArgs, List<String> defaultJvmOpts, String javaHome, Map<String, String> env, FileCollection classpath) {
        this.mainClass = mainClass
        this.args = args
        this.checkArgs = checkArgs
        this.defaultJvmOpts = defaultJvmOpts
        this.javaHome = javaHome
        this.env = env
        this.classpath = classpath
    }
}
