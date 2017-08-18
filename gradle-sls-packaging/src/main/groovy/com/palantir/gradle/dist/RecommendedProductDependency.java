package com.palantir.gradle.dist;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableRecommendedProductDependency.class)
@JsonDeserialize(as = ImmutableRecommendedProductDependency.class)
public interface RecommendedProductDependency {

    @JsonProperty("product-group")
    String productGroup();

    @JsonProperty("product-name")
    String productName();

    @JsonProperty("minimum-version")
    String minimumVersion();

    @JsonProperty("maximum-version")
    String maximumVersion();

    @JsonProperty("recommended-version")
    String recommendedVersion();

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends ImmutableRecommendedProductDependency.Builder {
    }

}
