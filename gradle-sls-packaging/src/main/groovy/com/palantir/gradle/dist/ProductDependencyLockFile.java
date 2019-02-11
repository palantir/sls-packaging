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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProductDependencyLockFile {

    private static final String HEADER = "# Run ./gradlew --write-locks to regenerate this file\n";

    public static String asString(
            List<ProductDependency> deps, Set<ProductId> servicesDeclaredInProject, String projectVersion) {
        return deps.stream().map(dep -> String.format(
                "%s:%s (%s, %s)",
                dep.getProductGroup(),
                dep.getProductName(),
                renderDepMinimumVersion(servicesDeclaredInProject, projectVersion, dep),
                dep.getMaximumVersion())).sorted().collect(Collectors.joining("\n", HEADER, "\n"));
    }

    private static String renderDepMinimumVersion(
            Set<ProductId> servicesDeclaredInProject, String projectVersion, ProductDependency dep) {
        ProductId productId = new ProductId(dep.getProductGroup(), dep.getProductName());
        if (servicesDeclaredInProject.contains(productId) && dep.getMinimumVersion().equals(projectVersion)) {
            return "$projectVersion";
        } else {
            return dep.getMinimumVersion();
        }
    }

    private ProductDependencyLockFile() {}
}
