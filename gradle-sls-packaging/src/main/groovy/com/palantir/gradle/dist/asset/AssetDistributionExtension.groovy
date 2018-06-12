/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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
package com.palantir.gradle.dist.asset

import com.palantir.gradle.dist.BaseDistributionExtension
import org.gradle.api.Project

class AssetDistributionExtension extends BaseDistributionExtension {

    private Map<String, String> assets = [:]

    AssetDistributionExtension(Project project) {
        super(project)
        productType("asset.v1")
    }

    Map<String, String> getAssets() {
        return assets
    }

    void assets(String relativeSourcePath) {
        this.assets.put(relativeSourcePath, relativeSourcePath)
    }

    void assets(String relativeSourcePath, String relativeDestinationPath) {
        this.assets.put(relativeSourcePath, relativeDestinationPath)
    }

    void setAssets(Map<String, String> assets) {
        this.assets = assets
    }
}
