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

package com.palantir.gradle.dist.asset

import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.gradle.dist.GradleIntegrationSpec
import com.palantir.gradle.dist.Versions
import spock.lang.Ignore

class AssetDistributionPluginIntegrationSpec extends GradleIntegrationSpec {

    def 'manifest file contains expected fields'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                serviceName 'asset-name'
            }
        '''.stripIndent()

        when:
        runTasks(':distTar', ':untar')

        then:
        String manifest = file('dist/asset-name-0.0.1/deployment/manifest.yml').text
        manifest.contains('"manifest-version" : "1.0"')
        manifest.contains('"product-group" : "service-group"')
        manifest.contains('"product-name" : "asset-name"')
        manifest.contains('"product-version" : "0.0.1"')
        manifest.contains('"product-type" : "asset.v1"')
    }

    def 'asset dirs are copied correctly'() {
        given:
        file("static/foo/bar") << "."
        file("static/baz/abc") << "."
        file("static/abc") << "overwritten file"
        file("static/abs") << "absolute path"
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                assets "static/foo", "maven"
                assets "static/baz", "maven"
                assets "static/abc", "maven"
                assets file("static/abs").getAbsolutePath(), "maven"
            }
        '''.stripIndent()

        when:
        runTasks(':distTar', ':untar')

        then:
        fileExists("dist/asset-name-0.0.1/asset/maven/abc")
        fileExists("dist/asset-name-0.0.1/asset/maven/bar")
        fileExists("dist/asset-name-0.0.1/asset/maven/abs")
        fileExists("dist/asset-name-0.0.1/deployment/manifest.yml")

        def lines = file("dist/asset-name-0.0.1/asset/maven/abc").readLines()
        lines.size() == 1
        lines.get(0) == "overwritten file"
    }

    def 'fails when asset and service plugins are used'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.sls-java-service-distribution'
                id 'com.palantir.sls-asset-distribution'
            }
        '''.stripIndent()

        when:
        def result = runTasksAndFail(":tasks")

        then:
        result.output.contains("The plugins 'com.palantir.sls-asset-distribution' and 'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.")
    }

    def 'can specify service dependencies'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << """
            distribution {
                productDependency {
                    productGroup = "group1"
                    productName = "name1"
                    minimumVersion = "1.0.0"
                    maximumVersion = "2.0.0"
                    recommendedVersion = "1.5.0"
                }
                productDependency {
                    productGroup = "group2"
                    productName = "name2"
                    minimumVersion = "1.0.0"
                    maximumVersion = "2.x.x"
                }
            }
        """.stripIndent()
        file('product-dependencies.lock').text = """\
        # Run ./gradlew writeProductDependenciesLocks to regenerate this file
        group1:name1 (1.0.0, 2.0.0)
        group2:name2 (1.0.0, 2.x.x)
        """.stripIndent()

        when:
        runTasks(':distTar', ':untar')

        then:
        def mapper = new ObjectMapper()
        def manifest = mapper.readValue(file('dist/asset-name-0.0.1/deployment/manifest.yml'), Map)

        def dep1 = manifest['extensions']['product-dependencies'][0]
        dep1['product-group'] == 'group1'
        dep1['product-name'] == 'name1'
        dep1['minimum-version'] == '1.0.0'
        dep1['maximum-version'] == '2.0.0'
        dep1['recommended-version'] == "1.5.0"

        def dep2 = manifest['extensions']['product-dependencies'][1]
        dep2['product-group'] == 'group2'
        dep2['product-name'] == 'name2'
        dep2['minimum-version'] == '1.0.0'
        dep2['maximum-version'] == '2.x.x'
    }

    /**
     * Note: in this test, we are not checking that we can resolve exactly the right artifact,
     * as that is tricky to get right, when the configuration being resolved doesn't set any required attributes.
     *
     * For instance, if java happens to be applied to the project, gradle will ALWAYS prefer the
     * runtimeElements variant (from configuration runtimeElements) so our {@code sls} variant won't be selected.
     * However, here we only care about testing that it can resolve to <i>something</i>, for the sole purpose of
     * extracting the version the resolved component.
     *
     * This test requires the gradle-consistent-versions plugin to be on the same classpath because {@link GcvUtils} is
     * used. As 'compileOnly' dependencies are not included by Gradle TestKit per default, we need to extend the
     * classpath for plugins under test in 'gradle-sls-packaging/build.gradle'.
     */
    @Ignore // plugins{} block provides classpath isolation so sls-asset-distribution cannot load gcv types!
    def 'dist project can be resolved through plain dependency when GCV is applied'() {
        buildFile << """
            plugins {
                id 'com.palantir.consistent-versions' version '${Versions.GRADLE_CONSISTENT_VERSIONS}'
            }
            
            configurations {
                fromOtherProject
            }
            dependencies {
                fromOtherProject project(':dist')
            }
            
            task verify {
                doLast {
                    configurations.fromOtherProject.resolve()
                }
            }
        """.stripIndent()

        helper.addSubproject('dist', '''
            plugins {
                id 'com.palantir.sls-asset-distribution'
            }
            
            version '0.0.1'
            distribution {
                serviceName "my-asset"
            }
        ''')

        runTasks('--write-locks')

        expect:
        runTasks(':verify')
    }

    private static createUntarBuildFile(buildFile) {
        buildFile << '''
            plugins {
                id 'com.palantir.sls-asset-distribution'
            }
            
            distribution {
                serviceName 'asset-name'
            }

            version "0.0.1"
            project.group = 'service-group'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from { tarTree(distTar.outputs.files.singleFile) }
                into "dist"
                dependsOn distTar
                duplicatesStrategy = 'WARN'
            }
        '''.stripIndent()
    }
}
