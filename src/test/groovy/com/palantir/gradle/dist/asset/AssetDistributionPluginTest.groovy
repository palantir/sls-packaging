package com.palantir.gradle.dist.asset

import com.fasterxml.jackson.databind.ObjectMapper
import com.palantir.gradle.dist.GradleTestSpec

class AssetDistributionPluginTest extends GradleTestSpec {

    def 'manifest file contains expected fields'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                serviceName 'asset-name'
            }
        '''.stripIndent()

        when:
        runSuccessfully(':distTar', ':untar')

        then:
        String manifest = file('dist/asset-name-0.0.1/deployment/manifest.yml', projectDir).text
        manifest.contains('"manifest-version": "1.0"')
        manifest.contains('"product-group": "service-group"')
        manifest.contains('"product-name": "asset-name"')
        manifest.contains('"product-version": "0.0.1"')
        manifest.contains('"product-type": "asset.v1"')
    }

    def 'asset dirs are copied correctly'() {
        given:
        file("static/foo/bar") << "."
        file("static/baz/abc") << "."
        file("static/abc") << "overwritten file"
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                assets "static/foo", "maven"
                assets "static/baz", "maven"
                assets "static/abc", "maven"
            }
        '''.stripIndent()

        when:
        runSuccessfully(':distTar', ':untar')

        then:
        file("dist/asset-name-0.0.1/asset/maven/abc").exists()
        file("dist/asset-name-0.0.1/asset/maven/bar").exists()
        def lines = file("dist/asset-name-0.0.1/asset/maven/abc").readLines()
        lines.size() == 1
        lines.get(0) == "overwritten file"
    }

    def 'fails when asset and service plugins are used'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'com.palantir.asset-distribution'
            }
        '''.stripIndent()

        when:
        def result = run(":tasks").buildAndFail()

        then:
        result.output.contains("The plugins 'com.palantir.asset-distribution' and 'com.palantir.java-distribution' cannot be used in the same Gradle project.")
    }

    def 'can specify service dependencies'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << """
            distribution {
                serviceDependency "group1", "name1", "1.0.0", "2.0.0"
                serviceDependency {
                    group = "group2"
                    name = "name2"
                    minVersion = "1.0.0"
                    maxVersion = "2.0.0"
                    recommendedVersion = "1.5.0"
                }
            }
        """.stripIndent()

        when:
        runSuccessfully(':distTar', ':untar')

        then:
        def mapper = new ObjectMapper()
        def manifest = mapper.readValue(file('dist/asset-name-0.0.1/deployment/manifest.yml', projectDir), Map)

        def dep1 = manifest['extensions']['service-dependencies'][0]
        dep1['group'] == 'group1'
        dep1['name'] == 'name1'
        dep1['minVersion'] == '1.0.0'
        dep1['maxVersion'] == '2.0.0'
        dep1['recommendedVersion'] == null

        def dep2 = manifest['extensions']['service-dependencies'][1]
        dep2['group'] == 'group2'
        dep2['name'] == 'name2'
        dep2['minVersion'] == '1.0.0'
        dep2['maxVersion'] == '2.0.0'
        dep2['recommendedVersion'] == "1.5.0"
    }

    private static createUntarBuildFile(buildFile) {
        buildFile << '''
            plugins {
                id 'com.palantir.asset-distribution'
            }
            
            distribution {
                serviceName 'asset-name'
            }

            version "0.0.1"
            project.group = 'service-group'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/asset-name-0.0.1.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()
    }
}
