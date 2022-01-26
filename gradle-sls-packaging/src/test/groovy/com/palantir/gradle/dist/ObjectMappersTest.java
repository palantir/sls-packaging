/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ObjectMappersTest {
    @Test
    void ensure_we_can_deserialise_with_unknown_properties() throws IOException {
        Path path = Files.createTempFile("prefix", ".json");
        Files.writeString(path, "{\"recommended-product-dependencies\":[],\"something-else\":3}");
        ObjectMappers.readRecommendedProductDependencies(path.toFile());
    }
}
