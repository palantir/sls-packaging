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

package com.palantir.gradle.dist.service.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import com.palantir.gradle.dist.service.gc.GcProfile
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import java.nio.file.Files
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class LaunchConfigTask extends DefaultTask {

    static final List<String> tmpdirJvmOpts = ['-Djava.io.tmpdir=var/data/tmp']

    static final List<String> loggingJvmOpts = [
            '-XX:ErrorFile=var/log/hs_err_pid%p.log'
    ]

    static final List<String> dnsJvmOpts = [
            // Set DNS cache TTL to 20s to account for systems such as RDS and other
            // AWS-managed systems that modify DNS records on failover.
            '-Dsun.net.inetaddr.ttl=20'
    ]

    static final List<String> dirs = ['var/data/tmp']

    // Reduce memory usage for some versions of glibc.
    // Default value is 8 * CORES.
    // See https://issues.apache.org/jira/browse/HADOOP-7154
    static final Map<String, String> defaultEnvironment = Collections.unmodifiableMap(['MALLOC_ARENA_MAX':'4'])

    @Input
    String mainClass

    @Input
    String serviceName

    @Input
    List<String> args

    @Input
    List<String> checkArgs

    @Input
    GcProfile gc

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
        group = JavaServiceDistributionPlugin.GROUP_NAME
        description = "Generates launcher-static.yml and launcher-check.yml configurations."
    }

    @EqualsAndHashCode
    @ToString
    static class StaticLaunchConfig {
        // keep in sync with StaticLaunchConfig struct in go-java-launcher
        String configType = "java"
        int configVersion = 1
        String mainClass
        String serviceName
        String javaHome
        List<String> classpath
        List<String> jvmOpts
        List<String> args
        Map<String, String> env
        List<String> dirs
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
        writeConfig(createConfig(getArgs(), assembleJvmOpts(), defaultEnvironment), getStaticLauncher())
        writeConfig(createConfig(getCheckArgs(), tmpdirJvmOpts + defaultJvmOpts, [:]), getCheckLauncher())
    }

    List<String> assembleJvmOpts() {
        return tmpdirJvmOpts + gc.gcJvmOpts() + loggingJvmOpts + dnsJvmOpts + defaultJvmOpts
    }

    void writeConfig(StaticLaunchConfig config, File scriptFile) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        def outfile = project.buildDir.toPath().resolve(scriptFile.toPath())
        Files.createDirectories(outfile.parent)
        outfile.withWriter { it ->
            mapper.writeValue(it, config)
        }
    }

    StaticLaunchConfig createConfig(List<String> args, List<String> jvmOpts, Map<String, String> defaultEnv) {
        StaticLaunchConfig config = new StaticLaunchConfig()
        config.mainClass = mainClass
        config.serviceName = serviceName
        config.javaHome = javaHome ?: ""
        config.args = args
        config.classpath = relativizeToServiceLibDirectory(classpath)
        config.jvmOpts = jvmOpts
        config.env = defaultEnv + env
        config.dirs = dirs
        return config
    }

    private static List<String> relativizeToServiceLibDirectory(FileCollection files) {
        def output = []
        files.each { output.add("service/lib/" + it.name) }
        return output
    }

    void configure(String mainClass,
                   String serviceName,
                   List<String> args,
                   List<String> checkArgs,
                   GcProfile gcProfile,
                   List<String> defaultJvmOpts,
                   String javaHome,
                   Map<String, String> env,
                   FileCollection classpath) {
        this.mainClass = mainClass
        this.serviceName = serviceName
        this.args = args
        this.checkArgs = checkArgs
        this.gc = gcProfile
        this.defaultJvmOpts = defaultJvmOpts
        this.javaHome = javaHome
        this.env = env
        this.classpath = classpath
    }
}
