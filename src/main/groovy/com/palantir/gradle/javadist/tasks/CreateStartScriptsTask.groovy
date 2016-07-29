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

import com.palantir.gradle.javadist.DistributionExtension
import com.palantir.gradle.javadist.JavaDistributionPlugin
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.jvm.application.tasks.CreateStartScripts

class CreateStartScriptsTask extends CreateStartScripts {

    CreateStartScriptsTask() {
        group = JavaDistributionPlugin.GROUP_NAME
        description = "Generates standard Java start scripts."

        doLast {
            ManifestClasspathJarTask manifestClasspathJarTask =
                    (ManifestClasspathJarTask) project.tasks.getByName('manifestClasspathJar')
            if (!manifestClasspathJarTask) {
                throw new GradleException("Required task not found: manifestClasspathJar")
            }

            if (distributionExtension().isEnableManifestClasspath()) {
                // Replace standard classpath with pathing jar in order to circumnavigate length limits:
                // https://issues.gradle.org/browse/GRADLE-2992
                def winScriptFile = project.file getWindowsScript()
                def winFileText = winScriptFile.text

                // Remove too-long-classpath and use pathing jar instead
                winFileText = winFileText.replaceAll('set CLASSPATH=.*', 'rem CLASSPATH declaration removed.')
                winFileText = winFileText.replaceAll(
                        '("%JAVA_EXE%" .* -classpath ")%CLASSPATH%(" .*)',
                        '$1%APP_HOME%\\\\lib\\\\' + manifestClasspathJarTask.archiveName + '$2')

                winScriptFile.text = winFileText
            }
        }
    }

    DistributionExtension distributionExtension() {
        return project.extensions.findByType(DistributionExtension)
    }

    @Input
    @Override
    public String getMainClassName() {
        return distributionExtension().mainClass
    }

    @Input
    @Override
    public String getApplicationName() {
        return distributionExtension().serviceName
    }

    @Input
    @Override
    public List<String> getDefaultJvmOpts() {
        return distributionExtension().defaultJvmOpts
    }

    @OutputDirectory
    @Override
    public File getOutputDir() {
        return new File("${project.buildDir}/scripts")
    }

    @InputFiles
    @Override
    public FileCollection getClasspath() {
        return project.tasks['jar'].outputs.files + project.configurations.runtime
    }
}