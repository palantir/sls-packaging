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

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ProductType {
    @JsonProperty("service.v1")
    SERVICE_V1,

    @JsonProperty("daemon.v1")
    DAEMON_V1,

    @JsonProperty("asset.v1")
    ASSET_V1,

    @JsonProperty("helm-chart.v1")
    HELM_CHART_V1,

    @JsonProperty("foundry-product.v1")
    FOUNDRY_PRODUCT_V1,
}
