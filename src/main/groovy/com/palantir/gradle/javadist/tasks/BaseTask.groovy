package com.palantir.gradle.javadist.tasks

import com.palantir.gradle.javadist.DistributionExtension
import org.gradle.api.DefaultTask

class BaseTask extends DefaultTask {
    DistributionExtension distributionExtension() {
        return project.extensions.findByType(DistributionExtension)
    }
}
