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

import org.gradle.api.tasks.bundling.Jar

/**
 * Produces a JAR whose manifest's {@code Class-Path} entry lists exactly the JARs produced by the project's runtime
 * configuration.
 */
class ManifestClasspathJarTask extends Jar {

    public ManifestClasspathJarTask() {
        appendix = 'manifest-classpath'
    }

    public void configure(DistributionExtension ext) {
        manifest.attributes("Class-Path": project.files(project.configurations.runtime)
            .collect { it.getName() }
            .join(' ') + ' ' + project.tasks.jar.archiveName
        )
    }
}
