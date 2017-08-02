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

import com.palantir.gradle.dist.service.JavaServiceDistributionPlugin
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.application.tasks.CreateStartScripts

class CreateStartScriptsTask {

    static CreateStartScripts createStartScriptsTask(Project project, String taskName) {
        return project.tasks.create(taskName, CreateStartScripts) { p ->
            p.group = JavaServiceDistributionPlugin.GROUP_NAME
            p.description = "Generates standard Java start scripts."
            p.setOutputDir(new File("${project.buildDir}/scripts"))
            p.setClasspath(project.tasks['jar'].outputs.files + project.configurations.runtimeClasspath)
        }
    }

    static void configure(CreateStartScripts startScripts, String mainClass, String serviceName, String javaHomeWin,
                          List<String> defaultJvmOpts, boolean isEnableManifestClasspath) {
        startScripts.configure {
            setMainClassName(mainClass)
            setApplicationName(serviceName)
            setDefaultJvmOpts(defaultJvmOpts)

            doLast {

                def winScriptFile = project.file getWindowsScript()
                def winFileText = winScriptFile.text

                if (javaHomeWin != null) {
                    def setJavaHomeString = "@rem Set JAVA_HOME to configured path.\n"
                    setJavaHomeString += 'set JAVA_HOME=' + javaHomeWin + '\n'
                    winFileText =  winFileText.replaceAll('if defined JAVA_HOME ', setJavaHomeString)
                }

                Jar manifestClasspathJarTask = project.tasks.getByName('manifestClasspathJar')
                if (!manifestClasspathJarTask) {
                    throw new GradleException("Required task not found: manifestClasspathJar")
                }

                if (isEnableManifestClasspath) {
                    // Replace standard classpath with pathing jar in order to circumnavigate length limits:
                    // https://issues.gradle.org/browse/GRADLE-2992

                    // Remove too-long-classpath and use pathing jar instead
                    winFileText = winFileText.replaceAll('set CLASSPATH=.*', 'rem CLASSPATH declaration removed.')
                    winFileText = winFileText.replaceAll(
                        '("%JAVA_EXE%" .* -classpath ")%CLASSPATH%(" .*)',
                        '$1%APP_HOME%\\\\lib\\\\' + manifestClasspathJarTask.archiveName + '$2')

                }
                winScriptFile.text = winFileText
            }
        }
    }
}
