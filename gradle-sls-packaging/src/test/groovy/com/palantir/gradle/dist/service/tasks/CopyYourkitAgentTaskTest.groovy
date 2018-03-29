package com.palantir.gradle.dist.service.tasks

import com.palantir.gradle.dist.GradleTestSpec
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class CopyYourkitAgentTaskTest extends GradleTestSpec {

    def 'copyYourkitAgent task is up to date if already run'() {
        setup:
        createUntarBuildFile(buildFile)

        when:
        BuildResult buildResult = run(':copyYourkitAgent').build()

        then:
        buildResult.task(':copyYourkitAgent').outcome == TaskOutcome.SUCCESS

        when:
        buildResult = run(':copyYourkitAgent').build()

        then:
        buildResult.task(':copyYourkitAgent').outcome == TaskOutcome.UP_TO_DATE
    }

    def 'Build produces libyjpagent file'() {
        given:
        createUntarBuildFile(buildFile)

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        file('dist/service-name-0.0.1').exists()
        file('dist/service-name-0.0.1/service/lib/linux-x86-64/libyjpagent.so').exists()
        file('dist/service-name-0.0.1/service/lib/linux-x86-64/libyjpagent.so').getBytes().length > 0
    }

    protected runSuccessfully(String... tasks) {
        BuildResult buildResult = run(tasks).build()
        tasks.each { buildResult.task(it).outcome == TaskOutcome.SUCCESS }
        return buildResult
    }

    private static createUntarBuildFile(buildFile) {
        buildFile << '''
            plugins {
                id 'com.palantir.sls-java-service-distribution'
                id 'java'
            }

            project.group = 'service-group'

            repositories { jcenter() }

            version '0.0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
            }

            sourceCompatibility = '1.7'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/service-name-0.0.1.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()
    }
}