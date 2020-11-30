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
package com.palantir.gradle.dist.pod;

import com.palantir.sls.versions.SlsVersion;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PodServiceDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, String> volumeMap = new LinkedHashMap<String, String>();
    private String productGroup = "";
    private String productName = "";
    private String productVersion = "";

    public PodServiceDefinition() {}

    public PodServiceDefinition(
            String productGroup, String productName, String productVersion, Map<String, String> volumeMap) {
        this.productGroup = productGroup;
        this.productName = productName;
        this.productVersion = productVersion;
        this.volumeMap = volumeMap;
    }

    public void isValid() {
        if (productGroup.isEmpty()) {
            throw new IllegalArgumentException("product group must be specified for pod service");
        }

        if (productName.isEmpty()) {
            throw new IllegalArgumentException("product name must be specified for pod service");
        }

        if (!SlsVersion.check(productVersion)) {
            throw new IllegalArgumentException(
                    "product version must be specified and be a valid SLS version for pod service");
        }
    }

    public String getProductGroup() {
        return productGroup;
    }

    public void setProductGroup(String productGroup) {
        this.productGroup = productGroup;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductVersion() {
        return productVersion;
    }

    public void setProductVersion(String productVersion) {
        this.productVersion = productVersion;
    }

    public Map<String, String> getVolumeMap() {
        return volumeMap;
    }

    public void setVolumeMap(Map<String, String> volumeMap) {
        this.volumeMap = volumeMap;
    }
}
