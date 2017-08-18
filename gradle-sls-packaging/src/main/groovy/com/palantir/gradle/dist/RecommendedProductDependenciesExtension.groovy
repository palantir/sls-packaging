package com.palantir.gradle.dist

import org.gradle.util.ConfigureUtil

class RecommendedProductDependenciesExtension {

    Set<RecommendedProductDependency> recommendedProductDependencies = []

    void productDependency(Closure<?> closure) {
        RecommendedProductDependency dep = new RecommendedProductDependency()
        ConfigureUtil.configureUsing(closure).execute(dep)
        dep.isValid()
        recommendedProductDependencies.add(dep)
    }

}
