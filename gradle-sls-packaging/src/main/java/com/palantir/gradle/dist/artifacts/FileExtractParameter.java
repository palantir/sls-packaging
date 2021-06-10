/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.dist.artifacts;

import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

public interface FileExtractParameter extends TransformParameters {
    @Input
    Property<String> getPathToExtract();
}
