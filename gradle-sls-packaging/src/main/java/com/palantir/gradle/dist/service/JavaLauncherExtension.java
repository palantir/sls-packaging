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
import com.palantir.logsafe.Preconditions;
import javax.lang.model.SourceVersion;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

/**
 * Configures a jvm based replacement for the <a href="https://github.com/palantir/go-java-launcher">go-java-launcher and go-init binaries</a>
 * which are packaged in distributions by default.
 * <p>
 * When all the extension properties are set, the go binaries are neither downloaded nor packaged, and
 * {@code service/bin/init.sh} invokes the configured main classes with the bundled jdk instead.
 * <p>
 * This requires the runtime JDK to be bundled by being provided in {@link JavaServiceDistributionExtension#getJdks()}.
 * If it is not provided, and this extension is configured, an exception will be thrown.
 */
public abstract class JavaLauncherExtension {

    /** Main class which behaves like the {@code go-java-launcher} binary. */
    public abstract Property<String> getLauncherMainClass();

    /** Main class which behaves like the {@code go-init} binary. */
    public abstract Property<String> getInitMainClass();

    /** Options to pass to the jvm when starting the launcher. */
    public abstract ListProperty<String> getJvmArgs();

    /**
     * Returns whether the launcher extension has been configured (i.e. either class is set).
     */
    public final Provider<Boolean> configured() {
        Provider<Boolean> launcherMainClassConfigured =
                getLauncherMainClass().map(s -> !Strings.isNullOrEmpty(s)).orElse(false);
        Provider<Boolean> initMainClassConfigured =
                getInitMainClass().map(s -> !Strings.isNullOrEmpty(s)).orElse(false);

        return launcherMainClassConfigured.zip(initMainClassConfigured, (f, s) -> f || s);
    }

    public final void launcherMainClass(String launcherMainClass) {
        Preconditions.checkArgument(
                SourceVersion.isName(launcherMainClass), "launcherMainClass must be a valid class name");
        getLauncherMainClass().set(launcherMainClass);
    }

    public final void initMainClass(String initMainClass) {
        Preconditions.checkArgument(SourceVersion.isName(initMainClass), "initMainClass must be a valid class name");
        getInitMainClass().set(initMainClass);
    }

    public final void jvmArg(String jvmArg) {
        getJvmArgs().add(jvmArg);
    }
}
