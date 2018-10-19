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

public class ResponseTime implements GcProfile {

    private int initiatingOccupancyFraction = 68;

    public ResponseTime() { }

    @Override
    public final List<String> gcJvmOpts() {
        return ImmutableList.of("-XX:+UseParNewGC",
                "-XX:+UseConcMarkSweepGC",
                "-XX:+UseCMSInitiatingOccupancyOnly",
                "-XX:CMSInitiatingOccupancyFraction=" + initiatingOccupancyFraction,
                "-XX:+CMSClassUnloadingEnabled",
                "-XX:+ExplicitGCInvokesConcurrent",
                "-XX:+ClassUnloadingWithConcurrentMark",
                "-XX:+CMSScavengeBeforeRemark");
    }

    public final void initiatingOccupancyFraction(int occipancyFraction) {
        this.initiatingOccupancyFraction = occipancyFraction;
    }

    public final int getInitiatingOccupancyFraction() {
        return initiatingOccupancyFraction;
    }

    public final void setInitiatingOccupancyFraction(int occipancyFraction) {
        this.initiatingOccupancyFraction = occipancyFraction;
    }

}
