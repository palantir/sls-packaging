/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist;

import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;

/**
 * Base test class for Gradle integration tests in the sls-packaging plugin.
 * This class provides common utilities for testing Gradle plugins.
 *
 * <p>The new testing framework automatically:
 * <ul>
 *   <li>Creates isolated project directories under build/gradle-plugin-testing
 *   <li>Creates settings.gradle files automatically
 *   <li>Runs tests with --warning-mode=all by default
 *   <li>Supports multi-version Gradle testing (configured in build.gradle via gradleTestUtils)
 *   <li>Provides parameter injection for GradleInvoker, RootProject, and SubProject
 * </ul>
 *
 * <p>Note: The minimum Gradle version testing is now handled by the gradleTestUtils
 * configuration in build.gradle. The framework will automatically test against
 * the versions specified there, including SlsBaseDistPlugin.MINIMUM_GRADLE.
 */
@GradlePluginTests
@DisabledConfigurationCache
class GradleIntegrationTest {
    // No setup() method needed - the framework handles all initialization automatically.
    // The old setup() method created a settings file and MultiProjectIntegrationHelper,
    // both of which are now handled by the framework.

    /**
     * Helper method to check if a file exists in the project directory.
     * This is provided for compatibility with tests that were using the old framework.
     *
     * <p>Note: Consider using the fluent assertion API instead:
     * <pre>{@code
     * project.file("path").assertThat().exists()
     * }</pre>
     */
    protected boolean fileExists(RootProject project, String path) {
        return project.file(path).path().toFile().exists();
    }

    // No createRunner() method needed - the new framework automatically runs all builds
    // with --warning-mode=all. The GradleInvoker parameter injection handles this automatically.
}
