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

package com.palantir.gradle.dist.service

import spock.lang.Specification

class JavaServiceDistributionExtensionTest extends Specification {
    def 'collection modifiers are cumulative when varargs are given'() {
        given:
        def ext = new JavaServiceDistributionExtension(null)

        when:
        ext.with {
            args 'a', 'b'
            args 'c', 'd'

            checkArgs 'a', 'b'
            checkArgs 'c', 'd'

            defaultJvmOpts 'a', 'b'
            defaultJvmOpts 'c', 'd'

            excludeFromVar 'a', 'b'
            excludeFromVar 'c', 'd'

            env 'a': 'b'
            env 'c': 'd'

            manifestExtensions 'a': 'b'
            manifestExtensions 'c': 'd'
        }

        then:
        ext.args == ['a', 'b', 'c', 'd']
        ext.checkArgs == ['a', 'b', 'c', 'd']
        ext.defaultJvmOpts == ['a', 'b', 'c', 'd']
        ext.excludeFromVar == ['log', 'run', 'a', 'b', 'c', 'd']
        ext.env == ['a': 'b', 'c': 'd']
        ext.manifestExtensions == ['a': 'b', 'c': 'd']
    }

    def 'collection setters replace existing data'() {
        given:
        def ext = new JavaServiceDistributionExtension(null)

        when:
        ext.with {
            setArgs(['a', 'b'])
            setArgs(['c', 'd'])
            setCheckArgs(['a', 'b'])
            setCheckArgs(['c', 'd'])
            setDefaultJvmOpts(['a', 'b'])
            setDefaultJvmOpts(['c', 'd'])
            setExcludeFromVar(['a', 'b'])
            setExcludeFromVar(['c', 'd'])
            setEnv(['a': 'b', 'c': 'd'])
            setEnv(['foo': 'bar'])
            setManifestExtensions(['a': 'b', 'c': 'd'])
            setManifestExtensions(['foo': 'bar'])
        }

        then:
        ext.args == ['c', 'd']
        ext.checkArgs == ['c', 'd']
        ext.defaultJvmOpts == ['c', 'd']
        ext.excludeFromVar == ['c', 'd']
        ext.env == ['foo': 'bar']
    }

    def 'service can be a daemon'() {
        given:
        def ext = new JavaServiceDistributionExtension(null)

        when:
        ext.with {
            productType 'daemon.v1'
        }

        then:
        ext.productType == 'daemon.v1'
    }
}
