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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import org.gradle.api.JavaVersion;

public interface GcProfile extends Serializable {
    long serialVersionUID = 1L;

    @VisibleForTesting
    Map<String, Class<? extends GcProfile>> PROFILE_NAMES = ImmutableMap.of(
            "throughput", GcProfile.Throughput.class,
            "response-time", GcProfile.ResponseTime.class,
            "hybrid", GcProfile.Hybrid.class);

    List<String> gcJvmOpts(JavaVersion javaVersion);

    class Throughput implements GcProfile {
        @Override
        public final List<String> gcJvmOpts(JavaVersion javaVersion) {
            return ImmutableList.of("-XX:+UseParallelOldGC");
        }
    }

    class ResponseTime implements GcProfile {
        private int newRatio = 2;
        private int initiatingOccupancyFraction = 68;

        @Override
        public final List<String> gcJvmOpts(JavaVersion javaVersion) {
            // The CMS garbage collector was removed in Java 14: https://openjdk.java.net/jeps/363. Users are free to
            // use it up until this release.
            if (javaVersion.compareTo(JavaVersion.toVersion("14")) >= 0) {
                return ImmutableList.of(
                        // https://wiki.openjdk.java.net/display/shenandoah/Main
                        "-XX:+UseShenandoahGC",
                        // "forces concurrent cycle instead of Full GC on System.gc()"
                        "-XX:+ExplicitGCInvokesConcurrent",
                        "-XX:+ClassUnloadingWithConcurrentMark",
                        "-XX:+UseNUMA");
            }
            return ImmutableList.of(
                    "-XX:+UseParNewGC",
                    "-XX:+UseConcMarkSweepGC",
                    /*
                     * When setting UseConcMarkSweepGC the default value of NewRatio (2) is completely ignored.
                     *
                     * https://bugs.openjdk.java.net/browse/JDK-8153578
                     */
                    "-XX:NewRatio=" + newRatio,
                    "-XX:+UseCMSInitiatingOccupancyOnly",
                    "-XX:CMSInitiatingOccupancyFraction=" + initiatingOccupancyFraction,
                    "-XX:+CMSClassUnloadingEnabled",
                    "-XX:+ExplicitGCInvokesConcurrent",
                    "-XX:+ClassUnloadingWithConcurrentMark",
                    "-XX:+CMSScavengeBeforeRemark",
                    // 'UseParNewGC' was removed in Java10: https://bugs.openjdk.java.net/browse/JDK-8173421
                    "-XX:+IgnoreUnrecognizedVMOptions");
        }

        public final void initiatingOccupancyFraction(int occupancyFraction) {
            this.initiatingOccupancyFraction = occupancyFraction;
        }

        public final void newRatio(int newerRatio) {
            this.newRatio = newerRatio;
        }
    }

    class Hybrid implements GcProfile {
        @Override
        public final List<String> gcJvmOpts(JavaVersion javaVersion) {
            return ImmutableList.of("-XX:+UseG1GC", "-XX:+UseStringDeduplication");
        }
    }
}
