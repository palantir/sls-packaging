package com.palantir.gradle.dist.asset

import com.palantir.gradle.dist.GradleTestSpec

class AssetDistributionPluginTest extends GradleTestSpec {

    def 'manifest file contains expected fields'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.asset-distribution'
            }

            project.group = 'service-group'

            version '0.1'

            distribution {
                serviceName 'asset-name'
            }

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/asset-name-0.1.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
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
        [file("static/foo/bar"), file("static/baz/abc")].each { it.write(".") }
        buildFile << '''
            plugins {
                id 'com.palantir.asset-distribution'
            }

            project.group = 'service-group'
            version '0.2'

            distribution {
                serviceName 'asset-name'
                assetDir "static/foo", "maven"
                assetDir "static/baz", "maven"
            }

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/asset-name-0.2.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()

        when:
        runSuccessfully(':distTar', ':untar')

        then:
        file("dist/asset-name-0.2/asset/maven/abc").exists()
        file("dist/asset-name-0.2/asset/maven/bar").exists()
    }


}
