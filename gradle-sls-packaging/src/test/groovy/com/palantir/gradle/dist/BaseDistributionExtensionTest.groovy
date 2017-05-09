package com.palantir.gradle.dist

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class BaseDistributionExtensionTest extends Specification {

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
}
