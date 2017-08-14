package com.palantir.gradle.dist

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BaseDistributionExtensionTest extends Specification {

    def 'serviceName uses project group as default'() {
        when:
        Project project = ProjectBuilder.builder().withName("foo").build()

        then:
        new BaseDistributionExtension(project).serviceName == "foo"
    }

    def 'serviceName can be overwritten'() {
        when:
        Project project = ProjectBuilder.builder().withName("foo").build()

        then:
        def ext = new BaseDistributionExtension(project)
        ext.serviceName("bar")
        ext.serviceName == "bar"
    }

    def 'serviceName in nested project'() {
        when:
        Project parent = ProjectBuilder.builder().withName("parent").build()
        Project child = ProjectBuilder.builder().withName("child").withParent(parent).build()

        then:
        new BaseDistributionExtension(child).serviceName == "child"
    }

    def 'serviceGroup uses project group as default'() {
        when:
        Project project = ProjectBuilder.builder().build()
        project.group = "foo"

        then:
        new BaseDistributionExtension(project).serviceGroup == "foo"
    }

    def 'serviceGroup can be overwritten'() {
        when:
        Project project = ProjectBuilder.builder().build()
        project.group = "foo"

        then:
        def ext = new BaseDistributionExtension(project)
        ext.serviceGroup("bar")
        ext.serviceGroup == "bar"
    }

    def 'productType only accepts valid values'() {
        when:
        def ext = new BaseDistributionExtension(null)
        ext.productType "foobar"

        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Invalid product type 'foobar' specified; supported types: [service.v1, asset.v1]."
    }

    def "productDependencies from invalid maven coordinate"() {
        when:
        def ext = new BaseDistributionExtension(null)
        ext.productDependency("group:name")

        then:
        thrown(IllegalArgumentException)
    }

    def "productDependencies from maven coordinate and no recommended version"() {
        when:
        def ext = new BaseDistributionExtension(null)
        ext.productDependency("group:name:1.2.3")

        then:
        ext.serviceDependencies.size() == 1
        ext.serviceDependencies.get(0).productGroup == "group"
        ext.serviceDependencies.get(0).productName == "name"
        ext.serviceDependencies.get(0).minimumVersion == "1.2.3"
        ext.serviceDependencies.get(0).maximumVersion == "1.x.x"
        ext.serviceDependencies.get(0).recommendedVersion == null
    }

    def "productDependencies from maven coordinate with all fields and no recommended version"() {
        when:
        def ext = new BaseDistributionExtension(null)
        ext.productDependency("group:name:1.2.3:classifier@tgz")

        then:
        ext.serviceDependencies.size() == 1
        ext.serviceDependencies.get(0).productGroup == "group"
        ext.serviceDependencies.get(0).productName == "name"
        ext.serviceDependencies.get(0).minimumVersion == "1.2.3"
        ext.serviceDependencies.get(0).maximumVersion == "1.x.x"
        ext.serviceDependencies.get(0).recommendedVersion == null
    }

    def "productDependencies from maven coordinate"() {
        when:
        def ext = new BaseDistributionExtension(null)
        ext.productDependency("group:name:1.2.3:classifier@tgz", "1.2.4")

        then:
        ext.serviceDependencies.size() == 1
        ext.serviceDependencies.get(0).productGroup == "group"
        ext.serviceDependencies.get(0).productName == "name"
        ext.serviceDependencies.get(0).minimumVersion == "1.2.3"
        ext.serviceDependencies.get(0).maximumVersion == "1.x.x"
        ext.serviceDependencies.get(0).recommendedVersion == "1.2.4"
    }
}
