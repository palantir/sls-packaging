package com.palantir.gradle.dist.tasks

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.palantir.gradle.dist.GradleTestSpec
import com.palantir.gradle.dist.RecommendedProductDependencies
import com.palantir.gradle.dist.RecommendedProductDependency
import nebula.test.dependencies.DependencyGraph
import nebula.test.dependencies.GradleDependencyGenerator
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class CreateManifestTaskTest extends GradleTestSpec {

    def jsonMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(new KebabCaseStrategy())
    def yamlMapper = new ObjectMapper(new YAMLFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(new KebabCaseStrategy())

    File mavenRepo

    def 'Fail on missing recommended product dependencies'() {
        setup:
        generateDependencies()
        buildFile << '''
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                maven {url "file:///{{mavenRepo}}"}
            }

            project.version = '1.0.0'

            dependencies {
                runtime 'a:a:1.0'
            }

            task testCreateManifest(type: com.palantir.gradle.dist.tasks.CreateManifestTask) {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = "service"
                manifestExtensions = [:]
                productDependencies = []
                productDependenciesConfig = configurations.runtime
            }
        '''.stripIndent().replace("{{mavenRepo}}", mavenRepo.getAbsolutePath())

        when:
        BuildResult buildResult = run(':testCreateManifest').buildAndFail()

        then:
        buildResult.task(':testCreateManifest').outcome == TaskOutcome.FAILED
        buildResult.output.contains("The following products are recommended as dependencies but do not appear in the " +
                "product dependencies or product dependencies ignored list: [group:name2, group:name]")
    }

    def 'Can ignore recommended product dependencies'() {
        setup:
        generateDependencies()
        buildFile << '''
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                maven {url "file:///{{mavenRepo}}"}
            }

            project.version = '1.0.0'

            dependencies {
                runtime 'a:a:1.0'
            }

            task testCreateManifest(type: com.palantir.gradle.dist.tasks.CreateManifestTask) {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = "service"
                manifestExtensions = [:]
                productDependencies = []
                productDependenciesConfig = configurations.runtime
                ignoredProductIds = [
                    new com.palantir.gradle.dist.ProductId("group:name"), 
                    new com.palantir.gradle.dist.ProductId("group:name2")
                ]
            }
        '''.stripIndent().replace("{{mavenRepo}}", mavenRepo.getAbsolutePath())

        when:
        BuildResult buildResult = run(':testCreateManifest').build()

        then:
        buildResult.task(':testCreateManifest').outcome == TaskOutcome.SUCCESS
    }

    def "Can set product dependencies from recommended product dependencies"() {
        setup:
        generateDependencies()
        buildFile << '''
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                maven {url "file:///{{mavenRepo}}"}
            }

            project.version = '1.0.0'

            dependencies {
                runtime 'a:a:1.0'
            }

            task testCreateManifest(type: com.palantir.gradle.dist.tasks.CreateManifestTask) {
                serviceName = "serviceName"
                serviceGroup = "serviceGroup"
                productType = "service"
                manifestExtensions = [:]
                productDependencies = [
                    new com.palantir.gradle.dist.ProductDependency("group", "name"),
                    new com.palantir.gradle.dist.ProductDependency("group", "name2")
                ]
                productDependenciesConfig = configurations.runtime
            }
        '''.stripIndent().replace("{{mavenRepo}}", mavenRepo.getAbsolutePath())

        when:
        runSuccessfully(':testCreateManifest')

        then:
        def manifest = jsonMapper.readValue(file('build/deployment/manifest.yml', projectDir).text, Map)
        manifest.get("extensions").get("product-dependencies").size() == 2
        manifest.get("extensions").get("product-dependencies") == [
                [
                        "product-group": "group",
                        "product-name": "name",
                        "minimum-version": "1.0.0",
                        "maximum-version": "1.x.x",
                        "recommended-version": "1.2.0"
                ],
                [
                        "product-group": "group",
                        "product-name": "name2",
                        "minimum-version": "2.0.0",
                        "maximum-version": "2.x.x",
                        "recommended-version": "2.2.0"
                ]
        ]
    }

    def 'Can create CreateManifestTask when product.version is valid SLS version'() {
        when:
        Project project = ProjectBuilder.builder().build()
        project.version = "1.0.0"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)

        then:
        task.getProjectVersion() == "1.0.0"
    }

    def 'Cannot create CreateManifestTask when product.version is invalid SLS version'() {
        when:
        Project project = ProjectBuilder.builder().build()
        project.version = "1.0.0foo"
        CreateManifestTask task = project.tasks.create("m", CreateManifestTask)
        task.getProjectVersion() == "1.0.0"

        then:
        IllegalArgumentException exception = thrown()
        exception.message == "Project version must be a valid SLS version: 1.0.0foo"
    }

    def generateDependencies() {
        DependencyGraph dependencyGraph = new DependencyGraph("a:a:1.0 -> b:b:1.0|c:c:1.0", "b:b:1.0", "c:c:1.0")
        GradleDependencyGenerator generator = new GradleDependencyGenerator(dependencyGraph)
        mavenRepo = generator.generateTestMavenRepo()

        def pdep1 = new File(mavenRepo, "a/a/1.0/a-1.0.pdep")
        pdep1.text = yamlMapper.writeValueAsString(RecommendedProductDependencies.builder()
                .addRecommendedProductDependencies(RecommendedProductDependency.builder()
                .productGroup("group")
                .productName("name")
                .minimumVersion("1.0.0")
                .maximumVersion("1.x.x")
                .recommendedVersion("1.2.0")
                .build())
                .build())
        def pdep2 = new File(mavenRepo, "c/c/1.0/c-1.0.pdep")
        pdep2.text = yamlMapper.writeValueAsString(RecommendedProductDependencies.builder()
                .addRecommendedProductDependencies(RecommendedProductDependency.builder()
                .productGroup("group")
                .productName("name2")
                .minimumVersion("2.0.0")
                .maximumVersion("2.x.x")
                .recommendedVersion("2.2.0")
                .build())
                .build())
    }
}
