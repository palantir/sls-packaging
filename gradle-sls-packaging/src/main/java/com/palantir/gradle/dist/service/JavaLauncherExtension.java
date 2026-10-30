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

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

/**
 * Configures a jvm based replacement for the <a href="https://github.com/palantir/go-java-launcher">go-java-launcher and go-init binaries</a>
 * which are packaged in distributions by default.
 * <p>
 * When a {@link #launcher(Object) launcher} is set, the go binaries are neither downloaded nor packaged, and
 * {@code service/bin/init.sh} invokes the configured main classes with the bundled jdk instead. Both main classes are
 * then required.
 * <p>
 * This requires the runtime JDK to be bundled by being provided in {@link JavaServiceDistributionExtension#getJdks()}.
 * If it is not provided, and this extension is configured, an exception will be thrown.
 */
public abstract class JavaLauncherExtension {

    /** Dependency notation of the launcher library, see {@link #launcher(Object)}. */
    public abstract Property<Object> getLauncher();

    /** Main class which behaves like the {@code go-java-launcher} binary. */
    public abstract Property<String> getLauncherMainClass();

    /** Main class which behaves like the {@code go-init} binary. */
    public abstract Property<String> getInitMainClass();

    /** Options to pass to the jvm when starting the launcher. */
    public abstract ListProperty<String> getJvmArgs();

    /**
     * The command running the {@link #getLauncherMainClass() launcher main class}, using locations relative to the
     * distribution root directory. Absent unless the java-based launcher is used.
     * <p>
     * Set by {@link Launchers}, read-only for consumers.
     */
    public abstract ListProperty<String> getLauncherCommand();

    /**
     * Uses the given library and its runtime dependencies as the java-based launcher, resolved through the
     * {@code javaLauncher} configuration (so with the rest of the dependencies under gradle-consistent-versions).
     *
     * @param dependencyNotation any dependency notation (e.g. {@code 'group:name'} or {@code project(':foo')}), or a
     *     {@link Provider} of one
     */
    public final void launcher(Object dependencyNotation) {
        if (dependencyNotation instanceof Provider<?> provider) {
            getLauncher().set(provider);
        } else {
            getLauncher().set(dependencyNotation);
        }
    }
}
