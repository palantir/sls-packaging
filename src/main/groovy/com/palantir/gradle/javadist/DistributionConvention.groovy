package com.palantir.gradle.javadist

import org.gradle.api.Project
import org.gradle.api.Task

import java.nio.file.Paths

class DistributionConvention {
    private static final String GROUP_NAME = "Distribution"

    private Project project
    private boolean configured = false

    DistributionConvention(Project project) {
        this.project = project
    }

    void distribution(Closure<?> closure) {
        if (configured) {
            throw new IllegalStateException("Cannot configure distribution twice!")
        }
        configured = true
        DistributionExtension ext = new DistributionExtension()
        project.configure(ext, closure)

        // Specify classpath using pathing jar rather than command line argument on Windows, since
        // Windows path sizes are limited.
        ManifestClasspathJarTask manifestClasspathJar = project.tasks.create("manifestClasspathJar", ManifestClasspathJarTask, {
            group = GROUP_NAME
            onlyIf { ext.isEnableManifestClasspath() }
            configure(ext)
        })

        Task startScripts = project.tasks.create('createStartScripts', DistributionCreateStartScriptsTask, {
            group = GROUP_NAME
            description = "Generates standard Java start scripts."
            configure(ext)
        }) << {
            if (ext.isEnableManifestClasspath()) {
                // Replace standard classpath with pathing jar in order to circumnavigate length limits:
                // https://issues.gradle.org/browse/GRADLE-2992
                def winScriptFile = project.file getWindowsScript()
                def winFileText = winScriptFile.text

                // Remove too-long-classpath and use pathing jar instead
                winFileText = winFileText.replaceAll('set CLASSPATH=.*', 'rem CLASSPATH declaration removed.')
                winFileText = winFileText.replaceAll(
                    '("%JAVA_EXE%" .* -classpath ")%CLASSPATH%(" .*)',
                    '$1%APP_HOME%\\\\lib\\\\' + manifestClasspathJar.archiveName + '$2')

                winScriptFile.text = winFileText
            }
        }

        Task initScript = project.tasks.create('createInitScript', {
            group = GROUP_NAME
            description = "Generates daemonizing init.sh script."
        }) << {
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/init.sh'),
                Paths.get("${project.buildDir}/scripts/init.sh"),
                ['@serviceName@': ext.serviceName,
                 '@args@': ext.args.iterator().join(' ')])
                .toFile()
                .setExecutable(true)
        }

        Task configScript = project.tasks.create('createConfigScript', {
            group = GROUP_NAME
            description = "Generates config.sh script."
        }) << {
            String javaHome = ext.javaHome != null ? 'JAVA_HOME="' + ext.javaHome + '"' : '#JAVA_HOME=""'
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/config.sh'),
                Paths.get("${project.buildDir}/scripts/config.sh"),
                ['@javaHome@': javaHome])
        }

        Task manifest = project.tasks.create('createManifest', {
            group = GROUP_NAME
            description = "Generates a simple yaml file describing the package content."
        }) << {
            EmitFiles.replaceVars(
                JavaDistributionPlugin.class.getResourceAsStream('/manifest.yml'),
                Paths.get("${project.buildDir}/deployment/manifest.yml"),
                ['@serviceName@': ext.serviceName,
                 '@serviceVersion@': String.valueOf(project.version)])
                .toFile()
        }

        project.tasks.create('distTar', DistTarTask, {
            group = GROUP_NAME
            description = "Creates a compressed, gzipped tar file that contains required runtime resources."
            dependsOn startScripts, initScript, configScript, manifest, manifestClasspathJar
            configure(ext)
        })

        project.tasks.create('run', RunTask, {
            group = GROUP_NAME
            description = "Runs the specified project using configured mainClass and with default args."
            configure(ext)
        })
    }
}
