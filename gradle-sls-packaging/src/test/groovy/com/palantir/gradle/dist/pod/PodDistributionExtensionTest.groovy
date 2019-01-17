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

package com.palantir.gradle.dist.pod

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class PodDistributionExtensionTest extends Specification {
    Project project;
    def setup() {
        project = ProjectBuilder.builder().build();
    }

    def 'collection modifiers are cumulative'() {
        given:
        def ext = new PodDistributionExtension(project)
        def barService = new PodServiceDefinition("com.palantir.foo", "bar", "1.0.0", [:])
        def bazService = new PodServiceDefinition("com.palantir.foo", "baz", "1.0.0", [:])

        when:
        ext.with {
            setService "bar", barService
            setService "baz", bazService
        }

        then:
        def ks = ext.getServices().get().keySet()
        ks.contains("bar")
        ks.contains("baz")
    }

    def 'collection setters replace existing data'() {
        given:
        def ext = new PodDistributionExtension(project)
        def barService = new PodServiceDefinition("com.palantir.foo", "bar", "1.0.0", [:])
        def bazService = new PodServiceDefinition("com.palantir.foo", "baz", "1.0.0", [:])

        when:
        ext.with {
            setServices(["bar": barService])
            setServices(["baz": bazService])
        }

        then:
        def ks = ext.getServices().get().keySet()
        ks.contains("baz")
        !ks.contains("bar")
    }
}
