/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.dist.artifacts;

import com.google.common.base.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.net.URI;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

public interface ArtifactLocator {
    @Input
    Property<String> getType();

    @Input
    Property<String> getUri();

    default void isValid() {
        Preconditions.checkNotNull(getType().get(), "type must be specified");
        Preconditions.checkNotNull(getUri().get(), "uri must be specified");
        uriIsValid(getUri().get());
    }

    private static void uriIsValid(String uri) {
        try {
            // Throws IllegalArgumentException if URI does not conform to RFC 2396
            URI.create(uri);
        } catch (IllegalArgumentException e) {
            throw new SafeIllegalArgumentException("uri is not valid", e);
        }
    }
}
