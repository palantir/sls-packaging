package com.palantir.gradle.dist.asset

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
        String manifest = file('dist/asset-name-0.1/deployment/manifest.yml', projectDir).text
        manifest.contains('"manifest-version": "1.0"')
        manifest.contains('"product-group": "service-group"')
        manifest.contains('"product-name": "asset-name"')
        manifest.contains('"product-version": "0.1"')
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
                serviceName 'asset-name'
                assets "static/foo", "maven"
                assets "static/baz", "maven"
                assets "static/abc", "maven"
            }
        '''.stripIndent()

        when:
        runSuccessfully(':distTar', ':untar')

        then:
        file("dist/asset-name-0.1/asset/maven/abc").exists()
        file("dist/asset-name-0.1/asset/maven/bar").exists()
        def lines = file("dist/asset-name-0.1/asset/maven/abc").readLines()
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
        result.output.contains("The Asset distribution and the Java Service distribution plugins cannot be used in the same Gradle project.")
    }

    private static def createUntarBuildFile(buildFile) {
        buildFile << '''
            plugins {
                id 'com.palantir.asset-distribution'
            }

            version 0.1
            project.group = 'service-group'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/asset-name-0.1.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()
    }
}
