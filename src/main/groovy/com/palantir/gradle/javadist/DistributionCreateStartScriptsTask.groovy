/*
 * Copyright 2015 Palantir Technologies
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
package com.palantir.gradle.javadist

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.jvm.application.tasks.CreateStartScripts

class DistributionCreateStartScriptsTask extends CreateStartScripts {

    private DistributionExtension ext

    public void configure(DistributionExtension ext) {
        this.ext = ext
    }

    @Input
    @Override
    public String getMainClassName() {
        return ext.mainClass
    }

    @Input
    @Override
    public String getApplicationName() {
        return ext.serviceName
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