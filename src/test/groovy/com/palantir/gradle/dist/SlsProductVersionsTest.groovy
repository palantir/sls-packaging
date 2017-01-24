package com.palantir.gradle.dist

import spock.lang.Specification

class SlsProductVersionsTest extends Specification {

    def 'verify orderable version detection'() {
        when: true
        then:
        SlsProductVersions.isOrderableVersion('2.0.0-20-gaaaaaa')
        SlsProductVersions.isOrderableVersion('2.0.0-20-gaaaaaa')
        SlsProductVersions.isOrderableVersion('1.2.4')
        SlsProductVersions.isOrderableVersion('2.0.0-beta1')
        SlsProductVersions.isOrderableVersion('2.0.0-rc1')
        SlsProductVersions.isOrderableVersion('2.0.0-beta1')

        !SlsProductVersions.isOrderableVersion(' 2.0.0')
        !SlsProductVersions.isOrderableVersion('2.0.0 ')
        !SlsProductVersions.isOrderableVersion('2.0.0-foo')
    }

    def 'verify non-orderable version detection'() {
        when: true
        then:
        SlsProductVersions.isNonOrderableVersion('2.0.0-20-gaaaaaa')
        SlsProductVersions.isNonOrderableVersion('2.0.0-20-gaaaaaa')
        SlsProductVersions.isNonOrderableVersion('1.2.4')
        SlsProductVersions.isNonOrderableVersion('2.0.0-beta1')
        SlsProductVersions.isNonOrderableVersion('2.0.0-rc1')
        SlsProductVersions.isNonOrderableVersion('2.0.0-beta1')
        SlsProductVersions.isNonOrderableVersion('2.0.0-foo')
        SlsProductVersions.isNonOrderableVersion('2.0.0-foo-g20-gaaaaaa')

        !SlsProductVersions.isNonOrderableVersion(' 2.0.0')
        !SlsProductVersions.isNonOrderableVersion('2.0.0 ')
    }

    def 'verify valid version detection'() {
        when: true
        then:
        SlsProductVersions.isValidVersion('1.2.4')
        SlsProductVersions.isValidVersion('2.0.0-foo-g20-gaaaaaa')

        !SlsProductVersions.isValidVersion(' 2.0.0')
        !SlsProductVersions.isValidVersion('2.0.0 ')
    }
}
