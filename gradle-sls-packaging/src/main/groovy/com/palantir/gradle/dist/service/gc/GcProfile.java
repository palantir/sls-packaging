/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service.gc;

import com.google.common.collect.ImmutableList;
import java.util.List;

public interface GcProfile {

    List<String> gcProfileJvmOpts();

    default List<String> gcJvmOpts() {
        return ImmutableList.<String>builder()
                .add("-XX:+CrashOnOutOfMemoryError")  // requires JDK 8u92+
                .add("-XX:+PrintGCDateStamps")
                .add("-XX:+PrintGCDetails")
                .add("-XX:-TraceClassUnloading")
                .add("-XX:+UseGCLogFileRotation")
                .add("-XX:GCLogFileSize=10M")
                .add("-XX:NumberOfGCLogFiles=10")
                .add("-Xloggc:var/log/gc-%t-%p.log")
                .add("-verbose:gc")
                .addAll(gcProfileJvmOpts())
                .build();
    }

}
