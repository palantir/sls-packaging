/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist;

import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.util.GradleVersion;

public class SlsBaseDistPlugin implements Plugin<Project> {

    /** The name of the outgoing configuration. This will include the SLS artifact being published. */
    public static final String SLS_CONFIGURATION_NAME = "sls";

    public static final String SLS_DIST_USAGE = "sls-dist";

    public static final GradleVersion MINIMUM_GRADLE = GradleVersion.version("6.1");

    @Override
    public final void apply(Project project) {
        Preconditions.checkState(
                GradleVersion.current().compareTo(MINIMUM_GRADLE) >= 0,
                "This gradle version is too old",
                SafeArg.of("currentVersion", GradleVersion.current()),
                SafeArg.of("minimumVersion", MINIMUM_GRADLE));

        Configuration slsConf = project.getConfigurations().create(SLS_CONFIGURATION_NAME);
        slsConf.setCanBeResolved(false);
        // Make it export a custom usage, to allow resolving it via variant-aware resolution.
        slsConf.getAttributes()
                .attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, SLS_DIST_USAGE));

        project.getDependencies()
                .getAttributesSchema()
                .attribute(Usage.USAGE_ATTRIBUTE, strategy -> strategy.getDisambiguationRules()
                        .add(SlsDisambiguationRule.class));
    }

    /**
     * Still support old consumers which don't declare a required usage, such as gradle-docker's docker configuration.
     */
    static final class SlsDisambiguationRule implements AttributeDisambiguationRule<Usage> {
        @Override
        public void execute(MultipleCandidatesDetails<Usage> details) {
            if (details.getConsumerValue() == null) {
                List<Usage> slsDistMatches = details.getCandidateValues().stream()
                        .filter(it -> it.getName().equals(SLS_DIST_USAGE))
                        .collect(Collectors.toList());
                if (slsDistMatches.size() == 1) {
                    details.closestMatch(slsDistMatches.get(0));
                }
            }
        }
    }
}
