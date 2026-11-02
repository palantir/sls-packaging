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

import com.google.common.base.Strings;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.immutables.value.Value;
import org.immutables.value.Value.Immutable;

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

    private final Provider<Boolean> isEnabled;

    @Inject
    public JavaLauncherExtension(Provider<Boolean> isJdkBundled) {
        this.isEnabled = getLauncher()
                .map(_notation -> true)
                .orElse(false)
                .map(launcherSet -> ImmutableJavaLauncherInputs.builder().launcherSet(launcherSet))
                .zip(isJdkBundled.orElse(false), ImmutableJavaLauncherInputs.Builder::jdkBundled)
                .zip(getLauncherMainClass().orElse(""), ImmutableJavaLauncherInputs.Builder::launcherMainClass)
                .zip(getInitMainClass().orElse(""), ImmutableJavaLauncherInputs.Builder::initMainClass)
                // This also runs validation checks on the various inputs
                .map(ImmutableJavaLauncherInputs.Builder::build)
                .map(JavaLauncherInputs::javaLauncherConfigured);
    }

    /**
     * Whether the java-based launcher is enabled and properly configured.
     */
    public final Provider<Boolean> getEnabled() {
        return isEnabled;
    }

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

    @Immutable
    interface JavaLauncherInputs {
        boolean launcherSet();

        boolean jdkBundled();

        String launcherMainClass();

        String initMainClass();

        @Value.Derived
        default boolean launcherMainClassSet() {
            return !Strings.isNullOrEmpty(launcherMainClass());
        }

        @Value.Derived
        default boolean initMainClassSet() {
            return !Strings.isNullOrEmpty(initMainClass());
        }

        @Value.Derived
        default boolean javaLauncherConfigured() {
            // Note: it would be enough to check if launcherSet is true here, but this is a bit clearer as to the intent
            return launcherSet() && launcherMainClassSet() && initMainClassSet();
        }

        @Value.Check
        default void check() {
            if (!launcherSet()) {
                if (launcherMainClassSet() || initMainClassSet()) {
                    throw new GradleException("'distribution.javaLauncher.launcher' must be specified when the"
                            + "java-based launcher is configured.");
                }
                return;
            }
            if (!jdkBundled()) {
                throw new GradleException(
                        "Jdks must be configured and included in the distribution to use the java-based launcher.");
            }
            if (!launcherMainClassSet()) {
                throw new GradleException("'distribution.javaLauncher.launcherMainClass' must be specified when the"
                        + " java-based launcher is configured.");
            }
            if (!initMainClassSet()) {
                throw new GradleException("'distribution.javaLauncher.initMainClass' must be specified when the"
                        + " java-based launcher is configured.");
            }
        }
    }
}
