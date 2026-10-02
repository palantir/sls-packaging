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

package com.palantir.gradle.dist.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class ExampleTouchService {

    /** A tiny service which we run as part of integration tests */
    public static void main(String[] args) throws IOException {
        Files.createFile(Paths.get(args[0]));
    }

    private ExampleTouchService() {}
}
