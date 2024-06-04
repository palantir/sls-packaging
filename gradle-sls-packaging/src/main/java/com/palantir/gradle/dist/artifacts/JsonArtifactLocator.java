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
import java.io.Serializable;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class JsonArtifactLocator implements Serializable {

    @JsonProperty("type")
    private String type;

    @JsonProperty("uri")
    private String uri;

    public JsonArtifactLocator(String type, String uri) {
        this.type = type;
        this.uri = uri;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JsonArtifactLocator)) {
            return false;
        }
        JsonArtifactLocator artifactLocator = (JsonArtifactLocator) other;
        return Objects.equals(type, artifactLocator.type) && Objects.equals(uri, artifactLocator.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, uri);
    }
}
