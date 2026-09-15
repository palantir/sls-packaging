/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.service;

import org.gradle.api.provider.Property;

/**
 * Configures a jvm based replacement for the go-java-launcher and go-init binaries which are packaged in
 * distributions by default. When {@link #getLauncherMainClass()} and {@link #getInitMainClass()} are set, the go
 * binaries are neither downloaded nor packaged, and {@code service/bin/init.sh} invokes the configured main classes
 * with java instead.
 */
public abstract class JavaLauncherExtension {

    /**
     * Dependency notation of the library providing the launcher, e.g. {@code com.foo:java-launcher:1.2.3}. The
     * library and its runtime dependencies are packaged into {@code service/lib/launcher} of the distribution.
     * Alternatively, dependencies may be added directly to the
     * {@value JavaServiceDistributionPlugin#JAVA_LAUNCHER_CONFIGURATION_NAME} configuration.
     */
    public abstract Property<String> getCoordinate();

    /** Main class which behaves like the {@code go-java-launcher} binary. */
    public abstract Property<String> getLauncherMainClass();

    /** Main class which behaves like the {@code go-init} binary. */
    public abstract Property<String> getInitMainClass();

    public final void coordinate(String coordinate) {
        getCoordinate().set(coordinate);
    }

    public final void launcherMainClass(String launcherMainClass) {
        getLauncherMainClass().set(launcherMainClass);
    }

    public final void initMainClass(String initMainClass) {
        getInitMainClass().set(initMainClass);
    }
}
