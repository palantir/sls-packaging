package com.palantir.gradle.dist;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
@JsonSerialize(as = ImmutableRecommendedProductDependencies.class)
@JsonDeserialize(as = ImmutableRecommendedProductDependencies.class)
public interface RecommendedProductDependencies {

    @JsonProperty("recommended-product-dependencies")
    Set<RecommendedProductDependency> recommendedProductDependencies();

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends ImmutableRecommendedProductDependencies.Builder {
    }

}
