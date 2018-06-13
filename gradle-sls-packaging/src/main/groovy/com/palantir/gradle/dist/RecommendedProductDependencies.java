package com.palantir.gradle.dist;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Set;
import org.immutables.value.Value;

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
