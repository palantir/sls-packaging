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

import java.util.List;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

/**
 * Configures a jvm based replacement for the <a href="https://github.com/palantir/go-java-launcher">go-java-launcher and go-init binaries</a>
 * which are packaged in distributions by default.
 * <p>
 * A Java-based launcher can be configured by setting the {@link Launchers#JAVA_LAUNCHER_CONFIGURATION} configuration.
 * If it is not provided, the below configuration is ignored.
 */
public abstract class JavaLauncherExtension {

    /** Options to pass to the jvm when starting the launcher. */
    public abstract ListProperty<String> getJvmArgs();

    /**
     * The command used to run the launcher, provided as:
     * <ul>
     *     <li>The Java binary path, relative to the distribution root (using the unix binary)</li>
     *     <li>The Java command line arguments (e.g. options from {@link #getJvmArgs()}, and the classpath)</li>
     *     <li>The class to execute (the launcher main class)</li>
     *     <li>The arguments to the launcher ({@code} service/bin/launcher-static.yml}
     *         and {@code "var/conf/launcher-custom.yml"})</li>
     * </ul>
     * This can be consumed by external plugins which may repackage the distribution and don't want to rebuild the
     * expected command line themselves.
     * <p>
     * No equivalent is currently provided for the cli main class - use the provided init.sh script instead.
     * <p>
     * This is expected to be set exactly once by {@link Launchers}.
     */
    public abstract ListProperty<String> getLauncherCommand();

    /** Set once by {@link Launchers}. */
    final void launcherCommand(Provider<List<String>> command) {
        getLauncherCommand().set(command);
        getLauncherCommand().disallowChanges();
    }
}
