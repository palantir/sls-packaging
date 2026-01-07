/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service.tasks;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableLaunchConfigInfo.class)
@JsonDeserialize(as = ImmutableLaunchConfigInfo.class)
public interface LaunchConfigInfo {
    // keep in sync with StaticLaunchConfig struct in go-java-launcher
    @Value.Default
    default String configType() {
        return "java";
    }

    @Value.Default
    default int configVersion() {
        return 1;
    }

    @Value.Default
    default List<String> dirs() {
        return ImmutableList.of("var/data/tmp");
    }

    String mainClass();

    String serviceName();

    String javaHome();

    List<String> classpath();

    List<String> jvmOpts();

    List<String> args();

    Map<String, String> env();

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends ImmutableLaunchConfigInfo.Builder {}
}
