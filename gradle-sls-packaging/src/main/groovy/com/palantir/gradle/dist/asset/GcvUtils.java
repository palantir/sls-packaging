/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.asset;

import com.palantir.gradle.versions.VersionsLockExtension;
import org.gradle.api.Project;

/**
 * Things that access GCV classes are done here in a separate class, so it doesn't prevent groovy from loading and
 * decorating the plugin class.
 */
final class GcvUtils {
    /**
     * If GCV is applied, we want to lock the asset configuration.
     * This is to wrong around a bug where, during --write-locks, the locks from locked configurations don't get
     * exposed to consumers (as constraints) and so non-locked configurations get different versions than when
     * running without {@code --write-locks}.
     */
    public static void lockConfigurationInGcv(Project project) {
        project.getExtensions().configure(VersionsLockExtension.class, lockExt -> {
            lockExt.production(prod -> prod.from(AssetDistributionPlugin.ASSET_CONFIGURATION));
        });
    }

    private GcvUtils() {}
}
