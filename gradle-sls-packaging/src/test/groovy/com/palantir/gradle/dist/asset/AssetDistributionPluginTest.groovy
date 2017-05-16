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
        runSuccessfully(':distTar', ':untar')

        then:
        file("dist/asset-name-0.0.1/asset/maven/abc").exists()
        file("dist/asset-name-0.0.1/asset/maven/bar").exists()
        file("dist/asset-name-0.0.1/asset/maven/abs").exists()

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
        def result = run(":tasks").buildAndFail()

        then:
        result.output.contains("The plugins 'com.palantir.sls-asset-distribution' and 'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.")
    }

    def 'can specify service dependencies'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << """
            distribution {
                productDependency "group1", "name1", "1.0.0", "2.0.0"
                productDependency {
                    productGroup = "group2"
                    productName = "name2"
                    minimumVersion = "1.0.0"
                    maximumVersion = "2.0.0"
                    recommendedVersion = "1.5.0"
                }
                productDependency {
                    productGroup = "group3"
                    productName = "name3"
                }
            }
        """.stripIndent()

        when:
        runSuccessfully(':distTar', ':untar')

        then:
        def mapper = new ObjectMapper()
        def manifest = mapper.readValue(file('dist/asset-name-0.0.1/deployment/manifest.yml', projectDir), Map)

        def dep1 = manifest['extensions']['product-dependencies'][0]
        dep1['product-group'] == 'group1'
        dep1['product-name'] == 'name1'
        dep1['minimum-version'] == '1.0.0'
        dep1['maximum-version'] == '2.0.0'
        dep1['recommended-version'] == null

        def dep2 = manifest['extensions']['product-dependencies'][1]
        dep2['product-group'] == 'group2'
        dep2['product-name'] == 'name2'
        dep2['minimum-version'] == '1.0.0'
        dep2['maximum-version'] == '2.0.0'
        dep2['recommended-version'] == "1.5.0"

        def dep3 = manifest['extensions']['product-dependencies'][2]
        dep3['product-group'] == 'group3'
        dep3['product-name'] == 'name3'
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
                from tarTree(resources.gzip("${buildDir}/distributions/asset-name-0.0.1.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()
    }
}
