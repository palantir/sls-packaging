package com.palantir.gradle.javadist.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.palantir.gradle.javadist.JavaDistributionPlugin
import groovy.transform.EqualsAndHashCode
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
    public static class StaticLaunchConfig {
        // keep in sync with StaticLaunchConfig struct in go-java-launcher
        String configType = "java"
        int configVersion = 1
        String mainClass
        String javaHome
        List<String> classpath
        List<String> jvmOpts
        List<String> args
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
        return config
    }

    private static List<String> relativizeToServiceLibDirectory(FileCollection files) {
        def output = []
        files.each { output.add("service/lib/" + it.name) }
        return output
    }
}
