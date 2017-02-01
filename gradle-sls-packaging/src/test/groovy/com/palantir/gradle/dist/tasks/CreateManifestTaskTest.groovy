package com.palantir.gradle.dist.tasks

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class CreateManifestTaskTest extends Specification {

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
}
