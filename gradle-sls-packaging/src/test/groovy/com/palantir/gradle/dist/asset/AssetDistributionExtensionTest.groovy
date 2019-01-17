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

package com.palantir.gradle.dist.asset

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class AssetDistributionExtensionTest extends Specification {
    Project project = ProjectBuilder.builder().build();
    def 'collection modifiers are cumulative'() {
        given:
        def ext = new AssetDistributionExtension(project)

        when:
        ext.with {
            assets "path/to/foo"
            assets "path/to/src", "relocated/dest"
            assets "path/to/src2", "relocated/dest2"
        }

        then:
        ext.getAssets().get() == [
                "path/to/src" : "relocated/dest",
                "path/to/src2": "relocated/dest2",
                "path/to/foo" : "path/to/foo"
        ]
    }

    def 'collection setters replace existing data'() {
        given:
        def ext = new AssetDistributionExtension(project)

        when:
        ext.with {
            assets "path/to/src", "relocated/dest"
            setAssets(["path/to/src2": "relocated/dest2"])
        }

        then:
        ext.getAssets().get() == ["path/to/src2": "relocated/dest2"]
    }
}
