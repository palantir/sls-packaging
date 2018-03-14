/*
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.dist.pod

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.api.GradleException

@ToString
@EqualsAndHashCode
class PodVolumeDefinition implements Serializable {
    public static final String VOLUME_SIZE_REGEX = "^\\d+?(M|G|T)\$"

    private static final long serialVersionUID = 1L

    String desiredSize

    PodVolumeDefinition() {}

    boolean isValidPodVolumeDefinition() {
        return desiredSize.matches(VOLUME_SIZE_REGEX)
    }
}
