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
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import org.gradle.api.JavaVersion;

public interface GcProfile extends Serializable {
    long serialVersionUID = 1L;

    @VisibleForTesting
    ImmutableMap<String, Class<? extends GcProfile>> PROFILE_NAMES = ImmutableMap.of(
            "throughput", GcProfile.Throughput.class,
            "response-time", GcProfile.ResponseTime.class,
            "hybrid", GcProfile.Hybrid.class,
            "dangerous-no-profile", GcProfile.NoProfile.class);

    List<String> gcJvmOpts(JavaVersion javaVersion);

    class Throughput implements GcProfile {
        @Override
        public final List<String> gcJvmOpts(JavaVersion _javaVersion) {
            return ImmutableList.of("-XX:+UseParallelGC");
        }
    }

    class ResponseTime implements GcProfile {
        private int newRatio = 2;
        private int initiatingOccupancyFraction = 68;

        @Override
        public final List<String> gcJvmOpts(JavaVersion javaVersion) {
            // JDK-21+ uses generational ZGC as the response-time optimized garbage collector.
            if (javaVersion.compareTo(JavaVersion.toVersion("21")) >= 0) {
                return ImmutableList.of(
                        "-XX:+UseZGC",
                        // https://openjdk.org/jeps/439
                        "-XX:+ZGenerational",
                        // "forces concurrent cycle instead of Full GC on System.gc()"
                        "-XX:+ExplicitGCInvokesConcurrent");
            }

            // The CMS garbage collector was removed in Java 14: https://openjdk.java.net/jeps/363. Users are free to
            // use it up until this release.
            if (javaVersion.compareTo(JavaVersion.toVersion("14")) >= 0) {
                return ImmutableList.of(
                        "-XX:+UnlockExperimentalVMOptions",
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

    // Match the MaxGCPauseMillis case
    @SuppressWarnings("AbbreviationAsWordInName")
    class Hybrid implements GcProfile {
        // For Java versions older than 21 we use 500ms by default, up from the JDK default value of 200ms for
        // historical reasons. Java 21 builds seem to interact poorly with this value, causing frequent full
        // GC cycles. The 500ms value was chosen at a time when GC thread counts were based on cpu shares
        // rather than the underlying physical cores (prior to https://bugs.openjdk.org/browse/JDK-8281181),
        // so services tended to thrash in GC to meet the 200ms threshold, promoting data too quickly to old gen.
        // This is no longer the case, however we are leaving the Java 17 values where they've been, and making
        // changes targeting Java 21 and beyond where we've observed significant improvements.
        private static final int LEGACY_MAX_GC_PAUSE_MILLIS = 500;

        private OptionalInt maxGCPauseMillis = OptionalInt.empty();

        @Override
        public final List<String> gcJvmOpts(JavaVersion javaVersion) {
            ImmutableList.Builder<String> builder = ImmutableList.<String>builderWithExpectedSize(3)
                    .add("-XX:+UseG1GC")
                    .add("-XX:+UseNUMA");
            OptionalInt maxGCPauseMillisValue = getMaxGCPauseMillis(javaVersion);
            if (maxGCPauseMillisValue.isPresent()) {
                builder.add("-XX:MaxGCPauseMillis=" + maxGCPauseMillisValue.getAsInt());
            }
            return builder.build();
        }

        private OptionalInt getMaxGCPauseMillis(JavaVersion javaVersion) {
            if (maxGCPauseMillis.isPresent()) {
                return maxGCPauseMillis;
            }
            if (javaVersion.compareTo(JavaVersion.toVersion("21")) >= 0) {
                return OptionalInt.empty();
            } else {
                return OptionalInt.of(LEGACY_MAX_GC_PAUSE_MILLIS);
            }
        }

        public final void maxGCPauseMillis(int value) {
            this.maxGCPauseMillis = OptionalInt.of(value);
        }
    }

    /**
     * This GC profile does not apply any JVM flags which allows services to override GC settings without needing to
     * unset preconfigured flags.
     */
    class NoProfile implements GcProfile {
        @Override
        public final List<String> gcJvmOpts(JavaVersion _javaVersion) {
            return Collections.emptyList();
        }
    }
}
