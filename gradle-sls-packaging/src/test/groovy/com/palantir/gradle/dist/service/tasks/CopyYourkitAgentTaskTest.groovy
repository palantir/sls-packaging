package com.palantir.gradle.dist.service.tasks

import com.palantir.gradle.dist.GradleTestSpec
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

class CopyYourkitAgentTaskTest extends GradleTestSpec {

    def 'copyYourkitAgent task is up to date if already run'() {
        setup:
        createBuildFile(buildFile, "java-service", "foo-service")

        when:
        BuildResult buildResult = run(':copyYourkitAgent').build()

        then:
        buildResult.task(':copyYourkitAgent').outcome == TaskOutcome.SUCCESS

        when:
        buildResult = run(':copyYourkitAgent').build()

        then:
        buildResult.task(':copyYourkitAgent').outcome == TaskOutcome.UP_TO_DATE
    }

    private static createBuildFile(buildFile, pluginType, name) {
        buildFile << """
            plugins {
                id 'com.palantir.sls-${pluginType}-distribution'
            }

            distribution {
                serviceName '${name}'
            }

            version "0.0.1"
            project.group = 'service-group'
        """.stripIndent()
    }
}