package com.palantir.gradle.javadist

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import groovy.transform.EqualsAndHashCode
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.AbstractTask
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

class LaunchConfigTask extends AbstractTask {

    @EqualsAndHashCode
    public static class LaunchConfig {
        // keep in sync with LaunchConfig struct in go-java-launcher
        String configType = "java"
        int configVersion = 1
        String serviceName
        String mainClass
        String javaHome
        List<String> args
        List<String> classpath
        List<String> jvmOpts
    }

    private DistributionExtension ext

    public void configure(DistributionExtension extension) {
        this.ext = extension
    }

    @TaskAction
    void createConfig() {
        writeConfig(createConfig(ext.args), "launch/launcher.yml")
        writeConfig(createConfig(ext.checkArgs), "launch/launcher-check.yml")
    }

    void writeConfig(LaunchConfig config, String relativePath) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
        def outfile = project.buildDir.toPath().resolve(relativePath)
        Files.createDirectories(outfile.parent)
        outfile.withWriter { it ->
            mapper.writeValue(it, config)
        }
    }

    LaunchConfig createConfig(List<String> args) {
        LaunchConfig config = new LaunchConfig()
        config.serviceName = ext.serviceName
        config.mainClass = ext.mainClass
        config.javaHome = ext.javaHome ?: ""
        config.args = args
        config.classpath = relativizeToServiceLibDirectory(
                project.tasks[JavaPlugin.JAR_TASK_NAME].outputs.files + project.configurations.runtime)
        config.jvmOpts = ext.defaultJvmOpts
        return config
    }

    private static List<String> relativizeToServiceLibDirectory(FileCollection files) {
        def output = []
        files.each { output.add("service/lib/" + it.name) }
        return output
    }
}
