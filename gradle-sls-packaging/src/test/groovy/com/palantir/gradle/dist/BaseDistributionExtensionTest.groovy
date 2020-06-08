/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist

import com.palantir.logsafe.exceptions.SafeRuntimeException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BaseDistributionExtensionTest extends Specification {
    Project project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    def 'serviceName uses project group as default'() {
        new BaseDistributionExtension(project).distributionServiceName.get() == "foo"
    }

    def 'serviceName can be overwritten'() {
        def ext = new BaseDistributionExtension(project)
        ext.distributionServiceName("bar")
        ext.distributionServiceName == "bar"
    }

    def 'serviceName in nested project'() {
        when:
        Project parent = ProjectBuilder.builder().withName("parent").build()
        Project child = ProjectBuilder.builder().withName("child").withParent(parent).build()

        then:
        new BaseDistributionExtension(child).distributionServiceName.get() == "child"
    }

    def 'serviceGroup uses project group as default'() {
        when:
        project.group = "foo"

        then:
        new BaseDistributionExtension(project).distributionServiceGroup.get() == "foo"
    }

    def 'serviceGroup can be overwritten'() {
        when:
        project.group = "foo"

        then:
        def ext = new BaseDistributionExtension(project)
        ext.setServiceGroup("bar")
        ext.distributionServiceGroup.get() == "bar"
    }

    def "productDependencies from invalid maven coordinate"() {
        when:
        def ext = new BaseDistributionExtension(project)
        ext.productDependency("group:name")

        then:
        thrown(IllegalArgumentException)
    }

    def "productDependencies from maven coordinate and no recommended version"() {
        when:
        def ext = new BaseDistributionExtension(project)
        ext.productDependency("group:name:1.2.3")

        then:
        def productDependencies = ext.getAllProductDependencies().get()
        productDependencies.size() == 1
        productDependencies.get(0).productGroup == "group"
        productDependencies.get(0).productName == "name"
        productDependencies.get(0).minimumVersion == "1.2.3"
        productDependencies.get(0).maximumVersion == "1.x.x"
        productDependencies.get(0).recommendedVersion == null
    }

    def "productDependencies from maven coordinate with all fields and no recommended version"() {
        when:
        def ext = new BaseDistributionExtension(project)
        ext.productDependency("group:name:1.2.3:classifier@tgz")

        then:
        def productDependencies = ext.getAllProductDependencies().get()
        productDependencies.size() == 1
        productDependencies.get(0).productGroup == "group"
        productDependencies.get(0).productName == "name"
        productDependencies.get(0).minimumVersion == "1.2.3"
        productDependencies.get(0).maximumVersion == "1.x.x"
        productDependencies.get(0).recommendedVersion == null
    }

    def "productDependencies from maven coordinate"() {
        when:
        def ext = new BaseDistributionExtension(project)
        ext.productDependency("group:name:1.2.3:classifier@tgz", "1.2.4")

        then:
        def productDependencies = ext.getAllProductDependencies().get()
        productDependencies.size() == 1
        productDependencies.get(0).productGroup == "group"
        productDependencies.get(0).productName == "name"
        productDependencies.get(0).minimumVersion == "1.2.3"
        productDependencies.get(0).maximumVersion == "1.x.x"
        productDependencies.get(0).recommendedVersion == "1.2.4"
    }

    def "productDependencies from closure infers max version"() {
        when:
        def ext = new BaseDistributionExtension(project)
        ext.productDependency {
            productGroup = 'group'
            productName = 'name'
            minimumVersion = '2.5.1'
        }

        then:
        def productDependencies = ext.getAllProductDependencies().get()
        productDependencies.size() == 1
        productDependencies.get(0).productGroup == "group"
        productDependencies.get(0).productName == "name"
        productDependencies.get(0).minimumVersion == "2.5.1"
        productDependencies.get(0).maximumVersion == "2.x.x"
        productDependencies.get(0).recommendedVersion == null
    }

    def "productDependencies from closure fails if no minimum version"() {
        def ext = new BaseDistributionExtension(project)
        ext.productDependency {
            productGroup = 'group'
            productName = 'name'
        }

        when:
        ext.getAllProductDependencies().get()

        then:
        def ex = thrown(SafeRuntimeException)
        ex.cause != null
        ex.cause.message.contains("minimumVersion must be specified")
    }

    def "productDependency is lazy, not evaluated at configuration-time"() {
        when:
        def ext = new BaseDistributionExtension(project)
        ext.productDependency {
            throw new Exception('productDependency was configured at configuration-time')
        }

        then:
        noExceptionThrown()
    }
}
