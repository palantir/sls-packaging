/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.pdeps

import com.palantir.gradle.dist.GradleIntegrationSpec
import com.palantir.gradle.dist.ProductDependency
import com.palantir.gradle.dist.RecommendedProductDependencies
import com.palantir.gradle.dist.Serializations
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator

class ResolveProductDependenciesIntegrationSpec extends GradleIntegrationSpec {

    public static final String MANIFEST_FILE_PATH = 'build/product-dependencies/pdeps-manifest.json'

    ProductDependencyTestFixture pdtf
    File manifestFile

    def setup() {
        debug = true
        manifestFile = new File(projectDir, MANIFEST_FILE_PATH)
        pdtf = new ProductDependencyTestFixture(projectDir, buildFile)
        pdtf.setup()
    }

    def 'consumes declared product dependencies'() {
        setup:
        pdtf.addStandardProductDependency()

        when:
        def buildResult = runTasks(':resolveProductDependencies')

        then:
        def productDependencies = readPDepManifest()
        !productDependencies.isEmpty()
    }

    def 'discovers project dependencies without compilation'() {
        given:
        addSubproject('child', """
        apply plugin: 'java'
        apply plugin: 'com.palantir.recommended-product-dependencies'
        
        recommendedProductDependencies {
            ${ProductDependencyTestFixture.STANDARD_PRODUCT_DEPENDENCY}
        }
        """.stripIndent())
        buildFile << """
        dependencies {
            implementation project('child')
        }
        """.stripIndent()

        when:
        def result = runTasks(':resolveProductDependencies')

        then:
        !result.tasks.contains(':child:jar')
        def productDependencies = readPDepManifest()
        !productDependencies.isEmpty()
    }

    def 'discovers external dependencies'() {
        given:
        GradleDependencyGenerator generator = new GradleDependencyGenerator(
                new DependencyGraph("a:a:1.0"), new File(projectDir, "build/testrepogen").toString())
        def mavenRepo = generator.generateTestMavenRepo()

        // depends on group:name:[1.0.0, 1.x.x]:1.2.0
        Files.copy(
                ResolveProductDependenciesIntegrationSpec.class.getResourceAsStream("/a-1.0.jar"),
                new File(mavenRepo, "a/a/1.0/a-1.0.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)

        buildFile << """
        repositories {
            maven {url "file:///${mavenRepo.getAbsolutePath()}"}
        }
        
        dependencies {
            implementation 'a:a:1.0'
        }
        """.stripIndent()

        when:
        def result = runTasks(':resolveProductDependencies')

        then:
        def productDependencies = readPDepManifest()
        !productDependencies.isEmpty()
    }

    def 'merges declared product dependencies'() {
        setup:
        buildFile << """
            distribution {
                $ProductDependencyTestFixture.STANDARD_PRODUCT_DEPENDENCY
                //add same with a different minimum version
                productDependency {
                    productGroup = 'group'
                    productName = 'name'
                    minimumVersion = '1.1.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = '1.2.0'
                }
            }
        """.stripIndent()

        when:
        def buildResult = runTasks('resolveProductDependencies')

        then:
        buildResult.task(':resolveProductDependencies').outcome == TaskOutcome.SUCCESS
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.size() == 1
        prodDeps.find{it.productName == 'name'} == new ProductDependency('group', 'name', '1.1.0', '1.x.x', '1.2.0')
    }

    def 'throws if declared dependency is also ignored'() {
        setup:
        pdtf.addStandardProductDependency()
        buildFile << """
            distribution {
                ignoredProductDependency('group:name')
            }
        """.stripIndent()

        when:
        def buildResult = runTasksAndFail(':resolveProductDependencies')

        then:
        buildResult.output.contains('Encountered product dependency declaration that was also ignored')
    }

    def 'throws if declared dependency is also optional'() {
        setup:
        pdtf.addStandardProductDependency()
        buildFile << """
            distribution {
                optionalProductDependency('group:name')
            }
        """.stripIndent()

        when:
        def buildResult = runTasksAndFail(':resolveProductDependencies')

        then:
        buildResult.output.contains('Encountered product dependency declaration that was also declared as optional')
    }

    def "throws if declared dependency on the same product"() {
        buildFile << """
            distribution {
                productDependency {
                    productGroup = 'serviceGroup'
                    productName = 'serviceName'
                    minimumVersion = '1.1.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = '1.2.0'
                }
            }
        """.stripIndent()

        when:
        def buildResult = runTasksAndFail(':resolveProductDependencies')

        then:
        buildResult.output.contains('Invalid for product to declare an explicit dependency on itself')
    }

    def 'Resolve unspecified productDependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }
        """.stripIndent()

        when:
        runTasks(':resolveProductDependencies')

        then:
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.size() == 2
        prodDeps.find{it.productName == 'name'} == new ProductDependency('group', 'name', '1.0.0', '1.x.x', '1.2.0')
        prodDeps.find{it.productName == 'name2'} == new ProductDependency('group', 'name2', '2.0.0', '2.x.x', '2.2.0')
    }

    def 'Merges declared productDependencies with discovered dependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }
            distribution {
                //add same with a different minimum version and optional
                productDependency {
                    productGroup = 'group'
                    productName = 'name'
                    minimumVersion = '1.1.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = '1.2.0'
                    optional = true
                }
            }
        """.stripIndent()

        when:
        def result = runTasks(':resolveProductDependencies')

        then:
        !result.output.contains(
                "Please remove your declared product dependency on 'group:name' because it is already provided by a jar dependency")
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.size() == 2
        def pd = prodDeps.find { it.productName == 'name' }
        pd.minimumVersion == '1.1.0'
        pd.optional
    }

    def 'Can ignore recommended product dependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }
            distribution {
                ignoredProductDependency('group:name')
                ignoredProductDependency('group:name2')
            }
        """.stripIndent()

        when:
        runTasks(':resolveProductDependencies')

        then:
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.isEmpty()
    }

    def 'Mark as optional product dependencies'() {
        setup:
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }
            distribution {
                optionalProductDependency('group:name')
                optionalProductDependency('group:name2')
            }
        """.stripIndent()

        when:
        runTasks(':resolveProductDependencies')

        then:
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.find{it.productName == 'name'}.optional
        prodDeps.find{it.productName == 'name2'}.optional
    }

    def "Merges duplicate discovered dependencies with same version"() {
        setup:
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
                runtime 'd:d:1.0'
            }
        """.stripIndent()

        when:
        runTasks(':resolveProductDependencies')

        then:
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.size() == 1
    }

    def "Merges duplicate discovered dependencies with different mergeable versions"() {
        setup:
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
                runtime 'e:e:1.0'
            }
        """.stripIndent()

        when:
        runTasks(':resolveProductDependencies')

        then:
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.size() == 1
        prodDeps[0].minimumVersion == '2.1.0'
        prodDeps[0].maximumVersion == '2.6.x'
    }

    def "Does not include self dependency"() {
        buildFile << """
            dependencies {
                runtime 'b:b:1.0'
            }
            // Configure this service to have the same coordinates as the (sole) dependency coming from b:b:1.0
            distribution {
                serviceGroup = "group"
                serviceName = "name2"
            }
        """.stripIndent()

        when:
        runTasks(':resolveProductDependencies')

        then:
        readPDepManifest().isEmpty()
    }

    @Unroll
    def 'filters out recommended product dependency on self (version: #projectVersion)'() {
        setup:
        buildFile << """
        allprojects {
            project.version = '$projectVersion'
        }
        """
        helper.addSubproject("foo-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'
            
            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'my-service'
                    minimumVersion = rootProject.version
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())
        def fooServerDir = helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            dependencies {
                compile project(':foo-api')
            }
            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'my-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())
        //set report file to that in the sub-project dir
        manifestFile = new File(fooServerDir, MANIFEST_FILE_PATH)

        when:
        runTasks(':foo-server:resolveProductDependencies', '-i')

        then: 'foo-server does not include transitively discovered self dependency'
        readPDepManifest().isEmpty()

        where:
        projectVersion << ['1.0.0-rc1.dirty', '1.0.0']
    }

    def 'merging two dirty product dependencies is not acceptable'() {
        buildFile << """
        allprojects {
            project.version = '1.0.0-rc1.dirty'
        }
        """
        helper.addSubproject("foo-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'
            
            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'foo-service'
                    minimumVersion = '0.0.0.dirty'
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())

        helper.addSubproject("other-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            dependencies {
                compile project(':foo-api')
            }
            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'other-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
                
                // Incompatible with the 0.0.0.dirty from the classpath
                productDependency('com.palantir.group', 'foo-service', '1.0.0.dirty', '1.x.x')
            }
        """.stripIndent())

        when:
        def result = runTasksAndFail('resolveProductDependencies')

        then:
        result.output.contains('Could not determine minimum version among two non-orderable minimum versions')
    }

    def "resolveProductDependencies discovers in repo product dependencies"() {
        setup:
        buildFile << """
        allprojects {
            project.version = '1.0.0'
            group "com.palantir.group"
        }
        """
        helper.addSubproject("bar-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'bar-service'
                    minimumVersion = '0.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())
        def fooServerDir = helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            
            dependencies {
                compile project(':bar-api')
            }

            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'foo-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())
        //set report file to that in the sub-project dir
        manifestFile = new File(fooServerDir, MANIFEST_FILE_PATH)

        when:
        def result = runTasks('foo-server:resolveProductDependencies')

        then:
        result.task(":foo-server:resolveProductDependencies").outcome == TaskOutcome.SUCCESS
        result.task(':bar-api:jar') == null

        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.size() == 1
        prodDeps[0] == new ProductDependency('com.palantir.group', 'bar-service', '0.0.0', '1.x.x', '1.0.0')
    }

    def "resolveProductDependencies discovers transitive in repo dependencies"() {
        setup:
        buildFile << """
        allprojects {
            project.version = '1.0.0'
            group "com.palantir.group"
        }
        """
        helper.addSubproject("bar-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'bar-service'
                    minimumVersion = '0.0.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())
        helper.addSubproject("bar-lib", """
            apply plugin: 'java'
            dependencies {
                compile project(':bar-api')
            }
        """.stripIndent())
        def fooServerDir = helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            
            dependencies {
                compile project(':bar-lib')
            }

            distribution {
                serviceGroup 'com.palantir.group'
                serviceName 'foo-service'
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
                args 'server', 'var/conf/my-service.yml'
            }
        """.stripIndent())
        manifestFile = new File(fooServerDir, MANIFEST_FILE_PATH)

        when:
        def result = runTasks('foo-server:resolveProductDependencies')

        then:
        result.task(":foo-server:resolveProductDependencies").outcome == TaskOutcome.SUCCESS
        result.task(':bar-api:jar') == null
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.size() == 1
        prodDeps[0] == new ProductDependency('com.palantir.group', 'bar-service', '0.0.0', '1.x.x', '1.0.0')
    }

    def 'use provided discovered dependencies'() {
        setup:
        pdtf.addStandardProductDependency()
        def overrideDepsFile = new File(projectDir, 'override-deps.json')
        def pd = new ProductDependency('com.palantir.group', 'other-service', '0.5.0', '2.x.x', '1.0.0')
        Serializations.writeRecommendedProductDependencies(RecommendedProductDependencies.of([pd]), overrideDepsFile)
        buildFile << """
            dependencies {
                runtime 'a:a:1.0'
            }
            resolveProductDependencies {
                productDependenciesFiles.setFrom(file('override-deps.json'))
            }
        """

        when:
        def result = runTasks('resolveProductDependencies')

        then:
        result.task(":resolveProductDependencies").outcome == TaskOutcome.SUCCESS
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.size() == 2
        prodDeps[0] == new ProductDependency('com.palantir.group', 'other-service', '0.5.0', '2.x.x', '1.0.0')
        prodDeps[1] == new ProductDependency('group', 'name', '1.0.0', '1.x.x', '1.2.0')
    }

    def 'handles multiple product dependencies when project version is dirty'() {
        setup:
        // Set project version to be non orderable sls version
        buildFile << """
        allprojects {
            project.version = '1.0.0.dirty'
            group "com.palantir.group"
        }
        """
        helper.addSubproject('bar-service', '''
            apply plugin: 'com.palantir.sls-java-service-distribution'
            
            dependencies {
                compile project(':bar-api')
            }

            distribution {
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
            }
        '''.stripIndent())
        helper.addSubproject('bar-lib', '''
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'bar-service'
                    minimumVersion = project.version
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        '''.stripIndent())
        helper.addSubproject("bar-api", """
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-recommended-dependencies'

            recommendedProductDependencies {
                productDependency {
                    productGroup = 'com.palantir.group'
                    productName = 'bar-service'
                    minimumVersion = '0.5.0'
                    maximumVersion = '1.x.x'
                    recommendedVersion = rootProject.version
                }
            }
        """.stripIndent())
        def fooServerDir = helper.addSubproject("foo-server", """
            apply plugin: 'com.palantir.sls-java-service-distribution'
            
            dependencies {
                compile project(':bar-api')
                compile project(':bar-lib')
            }

            distribution {
                mainClass 'com.palantir.foo.bar.MyServiceMainClass'
            }
        """.stripIndent())
        manifestFile = new File(fooServerDir, MANIFEST_FILE_PATH)

        when:
        def result = runTasks('foo-server:resolveProductDependencies')

        then:
        result.task(":foo-server:resolveProductDependencies").outcome == TaskOutcome.SUCCESS
        List<ProductDependency> prodDeps = readPDepManifest()
        prodDeps.size() == 1
        prodDeps[0] == new ProductDependency('com.palantir.group', 'bar-service', '1.0.0.dirty', '1.x.x', null)
    }

    private List<ProductDependency> readPDepManifest() {
        assert manifestFile.exists()
        return Serializations.readProductDependencyManifest(manifestFile).productDependencies();
    }
}
