package com.palantir.gradle.dist

import spock.lang.Specification

class ProductDependencyTest extends Specification {
    def 'max version may be matcher'() {
        when:
        new ProductDependency("", "", "1.2.3", "1.2.x", "1.2.3").isValid()

        then:
        true
    }

    def 'min version must not be matcher'() {
        when:
        new ProductDependency("", "", "1.2.x", "1.2.x", "1.2.3").isValid()

        then:
        thrown(IllegalArgumentException)
    }

    def 'recommended version must not be matcher'() {
        when:
        new ProductDependency("", "", "1.2.3", "1.2.x", "1.2.x").isValid()

        then:
        thrown(IllegalArgumentException)
    }

    def 'default maximumVersion'() {
        when:
        def dep = new ProductDependency("", "", "1.2.3", null, "1.2.4")

        then:
        dep.maximumVersion == "1.x.x"
    }

    def 'non-deafult maximumVersion'() {
        when:
        def dep = new ProductDependency("", "", "1.2.3", "2.x.x", "1.2.4")

        then:
        dep.maximumVersion == "2.x.x"
    }

}
