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

import static java.util.stream.Collectors.toList;

import com.google.common.base.Splitter;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProductDependencyLockFile {

    private static final String HEADER = "# Run ./gradlew writeProductDependenciesLocks to regenerate this file\n";
    public static final String PROJECT_VERSION = "$projectVersion";
    private static final Pattern LOCK_PATTERN = Pattern.compile(
            "^(?<group>[^:]+):(?<name>[^ ]+) \\((?<min>[^,]+), (?<max>[^\\)]+)\\)(?<optional> optional)?$");
    public static final String LOCK_FILE = "product-dependencies.lock";

    public static List<ProductDependency> fromString(String contents, String projectVersion) {
        return Splitter.on("\n").splitToList(contents).stream()
                .flatMap(line -> {
                    Matcher matcher = LOCK_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        return Stream.of(new ProductDependency(
                                matcher.group("group"),
                                matcher.group("name"),
                                matcher.group("min").equals(PROJECT_VERSION) ? projectVersion : matcher.group("min"),
                                matcher.group("max"),
                                null,
                                matcher.group("optional") != null));
                    }
                    return Stream.empty();
                })
                .collect(toList());
    }

    public static String asString(List<ProductDependency> deps, Set<ProductId> servicesDeclaredInProject) {
        return deps.stream()
                .map(dep -> String.format(
                        "%s:%s (%s, %s)%s",
                        dep.getProductGroup(),
                        dep.getProductName(),
                        renderDepMinimumVersion(servicesDeclaredInProject, dep),
                        dep.getMaximumVersion(),
                        dep.getOptional() ? " optional" : ""))
                .sorted()
                .collect(Collectors.joining("\n", HEADER, "\n"));
    }

    /**
     * If a product ends up taking a product dependency on another product that's published in the same repo then the
     * lock file would have to be regenerated every commit, such that all PRs will end up conflicting with each other.
     * And sometimes it is not even possible to know what the proper minimum version is in time to write the lock file.
     * To avoid this, we replace the minimum version of such dependencies with a placeholder, {@code $projectVersion}.
     */
    private static String renderDepMinimumVersion(Set<ProductId> servicesDeclaredInProject, ProductDependency dep) {
        ProductId productId = ProductId.of(dep);
        if (servicesDeclaredInProject.contains(productId)) {
            return PROJECT_VERSION;
        } else {
            return dep.getMinimumVersion();
        }
    }

    private ProductDependencyLockFile() {}
}
