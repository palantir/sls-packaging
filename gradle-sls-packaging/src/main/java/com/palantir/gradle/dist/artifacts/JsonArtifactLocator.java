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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.net.URISyntaxException;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(as = ImmutableJsonArtifactLocator.class)
@JsonSerialize(as = ImmutableJsonArtifactLocator.class)
public interface JsonArtifactLocator {

    String URI_IS_NOT_VALID_PREAMBLE = "uri is not valid: ";

    String type();

    String uri();

    @Value.Check
    default void isValid() {
        try {
            // Throws IllegalArgumentException if URI does not conform to RFC 2396
            URI.create(uri());
        } catch (IllegalArgumentException e) {
            if (!(e.getCause() instanceof URISyntaxException)) {
                throw new IllegalArgumentException("uri is invalid for some other reason", e);
            }

            URISyntaxException cause = (URISyntaxException) e.getCause();
            int problemIndex = cause.getIndex();
            String highlight =
                    String.format(" %s^%s", "-".repeat(problemIndex), "-".repeat(uri().length() - (problemIndex + 1)));
            throw new IllegalArgumentException(
                    String.format("%s\n'%s'\n%s", URI_IS_NOT_VALID_PREAMBLE, uri(), highlight), e);
        }
    }

    static JsonArtifactLocator from(ArtifactLocator artifactLocator) {
        return from(artifactLocator.getType().get(), artifactLocator.getUri().get());
    }

    static JsonArtifactLocator from(String type, String uri) {
        return ImmutableJsonArtifactLocator.builder().type(type).uri(uri).build();
    }
}
