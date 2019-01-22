/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.Maps;
import com.palantir.gradle.dist.BaseDistributionExtension;
import com.palantir.gradle.dist.ProductType;
import java.util.Map;
import javax.inject.Inject;
import org.gradle.api.Project;

public class AssetDistributionExtension extends BaseDistributionExtension {
    // TODO(forozco): Use MapProperty once our minimum supported version is 5.1
    private Map<String, String> assets;

    @Inject
    public AssetDistributionExtension(Project project) {
        super(project);
        assets = Maps.newHashMap();
        setProductType(ProductType.ASSET_V1);
    }

    public final Map<String, String> getAssets() {
        return assets;
    }

    public final void assets(String relativeSourcePath) {
        this.assets.put(relativeSourcePath, relativeSourcePath);
    }

    public final void assets(String relativeSourcePath, String relativeDestinationPath) {
        this.assets.put(relativeSourcePath, relativeDestinationPath);
    }

    public final void setAssets(Map<String, String> assets) {
        this.assets = assets;
    }
}
