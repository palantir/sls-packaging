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

package com.palantir.gradle.dist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import java.util.Set;
import org.immutables.value.Value;

// Automatically suppressed to unblock enforcement in new code
@SuppressWarnings("ImmutablesStyle")
@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonSerialize(as = ImmutableRecommendedProductDependencies.class)
@JsonDeserialize(as = ImmutableRecommendedProductDependencies.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public interface RecommendedProductDependencies {
    String SLS_RECOMMENDED_PRODUCT_DEPS_KEY = "Sls-Recommended-Product-Dependencies";

    @JsonProperty("recommended-product-dependencies")
    Set<ProductDependency> recommendedProductDependencies();

    final class Builder extends ImmutableRecommendedProductDependencies.Builder {}

    static Builder builder() {
        return new Builder();
    }

    static RecommendedProductDependencies of(List<ProductDependency> productDependencies) {
        return builder()
                .addAllRecommendedProductDependencies(productDependencies)
                .build();
    }
}
