package com.palantir.gradle.dist

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class ProductId implements Serializable {

    private static final long serialVersionUID = 1L

    String productGroup
    String productName

    ProductId(String productGroup, String productName) {
        this.productGroup = productGroup
        this.productName = productName
    }

    ProductId(String productId) {
        def split = productId.split(":")
        if (split.length != 2) {
            throw new IllegalArgumentException("Invalid product ID: " + split)
        }
        this.productGroup = split[0]
        this.productName = split[1]
    }

    @Override
    String toString() {
        return productGroup + ":" + productName
    }
}
