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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public final class ProductId implements Serializable {
    private static final Splitter SPLITTER = Splitter.on(':');

    private static final long serialVersionUID = 1L;
    private String productGroup;
    private String productName;

    public ProductId() {}

    public ProductId(String productId) {
        List<String> split = SPLITTER.splitToList(productId);
        if (split.size() != 2) {
            throw new IllegalArgumentException("Invalid product ID: " + split);
        }

        this.productGroup = split.get(0);
        this.productName = split.get(1);
    }

    public ProductId(String productGroup, String productName) {
        this.productGroup = productGroup;
        this.productName = productName;
    }

    @Override
    public String toString() {
        return productGroup + ":" + productName;
    }

    public void isValid() {
        Preconditions.checkNotNull(productGroup, "productGroup must be specified");
        Preconditions.checkNotNull(productName, "productName must be specified");
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ProductId productId = (ProductId) other;
        return Objects.equals(productGroup, productId.productGroup)
                && Objects.equals(productName, productId.productName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productGroup, productName);
    }
}
