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

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.palantir.sls.versions.SlsVersionMatcher;
import groovy.lang.Closure;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.util.GFileUtils;

public final class ProductDependencyIntrospectionPlugin implements Plugin<Project> {

    private static final String PRODUCT_DEPENDENCIES_CONFIGURATION = "productDependencies";

    @Override
    public void apply(Project project) {
        createGetMinimumProductVersion(project);

        project.getConfigurations().register(PRODUCT_DEPENDENCIES_CONFIGURATION, conf -> {
            conf.setCanBeResolved(false);
            conf.setDescription("Exposes minimum, maximum versions of product dependencies as constraints");
            conf.getDependencyConstraints().addAll(createAllProductConstraints(project));
        });
    }

    private static void createGetMinimumProductVersion(Project project) {
        project
                .getExtensions()
                .getExtraProperties()
                .set("getMinimumProductVersion", new Closure<String>(project, project) {

                    public String doCall(Object moduleVersion) {
                        List<String> strings = Splitter.on(':').splitToList(moduleVersion.toString());
                        Preconditions.checkState(strings.size() == 2,
                                "Expected 'group:name', found: %s",
                                moduleVersion.toString());

                        return getMinimumProductVersion(project, strings.get(0), strings.get(1));
                    }
                });
    }

    private static String getMinimumProductVersion(Project project, String group, String name) {
        List<ProductDependency> dependencies = getAllProductDependencies(project);

        Optional<ProductDependency> dependency = dependencies
                .stream()
                .filter(dep -> dep.getProductGroup().equals(group) && dep.getProductName().equals(name))
                .findAny();

        return dependency
                .orElseThrow(() -> new GradleException(String.format("Unable to find product dependency for '%s:%s'",
                        group,
                        name)))
                .getMinimumVersion();
    }

    private static List<ProductDependency> getAllProductDependencies(Project project) {
        File lockFile = project.file(ProductDependencyLockFile.LOCK_FILE);
        Preconditions.checkState(Files.exists(lockFile.toPath()),
                "%s does not exist. Run ./gradlew --write-locks to generate it.",
                ProductDependencyLockFile.LOCK_FILE);

        return ProductDependencyLockFile.fromString(GFileUtils.readFile(lockFile), project.getVersion().toString());
    }

    static List<DependencyConstraint> createAllProductConstraints(Project project) {
        List<ProductDependency> dependencies = getAllProductDependencies(project);
        return dependencies.stream().map(dependency -> {
            String coordinate = dependency.getProductGroup() + ":" + dependency.getProductName();
            return project.getDependencies().getConstraints().create(coordinate, constraint -> {
                constraint.version(version -> {
                    SlsVersionMatcher matcher = SlsVersionMatcher.valueOf(dependency.getMaximumVersion());

                    StringBuilder topRange = new StringBuilder();
                    if (matcher.getMajorVersionNumber().isPresent()) {
                        topRange.append(matcher.getMajorVersionNumber().getAsInt() + 1);
                        if (matcher.getMinorVersionNumber().isPresent()) {
                            topRange.append(".");
                            topRange.append(matcher.getMinorVersionNumber().getAsInt() + 1);
                            if (matcher.getPatchVersionNumber().isPresent()) {
                                topRange.append(".");
                                topRange.append(matcher.getPatchVersionNumber().getAsInt() + 1);
                            }
                        }
                    }
                    String range = "[" + dependency.getMinimumVersion() + "," + topRange + "[";

                    version.strictly(range);
                    version.require(dependency.getMinimumVersion());
                });
            });
        }).collect(Collectors.toList());
    }
}
