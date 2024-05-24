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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.Serializable;
import java.net.URI;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class ArtifactLocator implements Serializable {

    @JsonProperty("type")
    private String type;

    @JsonProperty("uri")
    private String uri;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public ArtifactLocator() {}

    public ArtifactLocator(String type, String uri) {
        this.type = type;
        this.uri = uri;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ArtifactLocator)) {
            return false;
        }
        ArtifactLocator artifactLocator = (ArtifactLocator) other;
        return Objects.equals(type, artifactLocator.type) && Objects.equals(uri, artifactLocator.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, uri);
    }

    public void isValid() {
        Preconditions.checkNotNull(type, "type must be specified");
        Preconditions.checkNotNull(uri, "uri must be specified");
        uriIsValid(uri);
    }

    public static void uriIsValid(String uri) {
        try {
            // Throws IllegalArgumentException if URI does not conform to RFC 2396
            URI.create(uri);
        } catch (IllegalArgumentException e) {
            throw new SafeIllegalArgumentException("uri is not valid", e);
        }
    }
}
