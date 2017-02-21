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

import java.nio.file.Files

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

class LaunchConfigTask extends ConventionTask {

    static final List<String> tmpdirJvmOpts = [
            '-Djava.io.tmpdir=var/data/tmp'
    ]

    static final List<String> gcJvmOpts = [
            '-XX:+CrashOnOutOfMemoryError',  // requires JDK 8u92+
            '-XX:+PrintGCDateStamps',
            '-XX:+PrintGCDetails',
            '-XX:-TraceClassUnloading',
            '-XX:+UseGCLogFileRotation',
            '-XX:GCLogFileSize=10M',
            '-XX:NumberOfGCLogFiles=10',
            '-Xloggc:var/log/gc-%t-%p.log',
            '-verbose:gc'
    ]

    private String mainClass
    private List<String> args
    private List<String> checkArgs
    private List<String> defaultJvmOpts
    private Map<String, String> env
    private String javaHome
    private FileCollection classpath

    @Input
    String getMainClass() {
        return mainClass
    }

    @Input
    List<String> getArgs() {
        return args
    }

    @Input
    List<String> getCheckArgs() {
        return checkArgs
    }

    @Input
    List<String> getDefaultJvmOpts() {
        return defaultJvmOpts
    }

    @Input
    Map<String, String> getEnv() {
        return env
    }

    @Input
    @Optional
    String getJavaHome() {
        return javaHome
    }

    @Input
    FileCollection getClasspath() {
        return classpath
    }

    void setClasspath(FileCollection classpath) {
        this.classpath = classpath
    }


    @EqualsAndHashCode
    @ToString
    static class StaticLaunchConfig {
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
    static File getStaticLauncher() {
        return new File("scripts/launcher-static.yml")
    }

    @OutputFile
    static File getCheckLauncher() {
        return new File("scripts/launcher-check.yml")
    }

    @TaskAction
    void createConfig() {
        writeConfig(createConfig(getArgs(), tmpdirJvmOpts + gcJvmOpts + getDefaultJvmOpts()), getStaticLauncher())
        writeConfig(createConfig(getCheckArgs(), tmpdirJvmOpts + getDefaultJvmOpts()), getCheckLauncher())
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
        config.mainClass = getMainClass()
        config.javaHome = getJavaHome() ?: ""
        config.args = args
        config.classpath = relativizeToServiceLibDirectory(getClasspath())
        config.jvmOpts = jvmOpts
        config.env = getEnv()
        return config
    }

    private static List<String> relativizeToServiceLibDirectory(FileCollection files) {
        def output = []
        files.each { output.add("service/lib/" + it.name) }
        return output
    }

}
