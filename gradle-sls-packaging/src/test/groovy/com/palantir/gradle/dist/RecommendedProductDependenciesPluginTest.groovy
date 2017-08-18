package com.palantir.gradle.dist

import com.palantir.gradle.dist.tasks.CreateManifestTask

import java.util.jar.Manifest
import java.util.zip.ZipFile

class RecommendedProductDependenciesPluginTest extends GradleTestSpec {

    def "Adds recommended product dependencies to manifest"() {
        buildFile << """
            plugins {
                id 'com.palantir.sls-recommended-dependencies'
            }

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'group'
                    productName = 'name'
                    minimumVersion = '1.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = '1.2.3'
                }
            }
        """.stripIndent()

        when:
        runSuccessfully(':jar')

        then:
        def jar = new File(projectDir, "build/libs/root-project.jar")
        jar.exists()
        def recommendedDeps = readRecommendedProductDeps(jar)
        recommendedDeps == "{\"recommended-product-dependencies\":[{\"product-group\":\"group\",\"product-name\":\"name\",\"minimum-version\":\"1.0.0\",\"maximum-version\":\"1.x.x\",\"recommended-version\":\"1.2.3\"}]}"
    }

    def readRecommendedProductDeps(File jarFile) {
        def zf = new ZipFile(jarFile)
        def manifestEntry = zf.getEntry("META-INF/MANIFEST.MF")
        def manifest = new Manifest(zf.getInputStream(manifestEntry))
        return manifest.getMainAttributes().getValue(CreateManifestTask.SLS_RECOMMENDED_PRODUCT_DEPS_KEY)
    }

}
