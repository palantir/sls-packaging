/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

/** Contract between producers of OCI coordinates and SLS distributions which reference them in a manifest. */
public final class ManifestOciArtifacts {

    /** Declarable configuration where consumers place dependencies on OCI artifact producers. */
    public static final String CONFIGURATION_NAME = "manifestOciArtifacts";

    /** Internal resolvable configuration used to resolve artifact coordinates. */
    static final String RESOLVABLE_CONFIGURATION_NAME = "manifestOciArtifactsResolvable";

    /** Usage attribute identifying an OCI artifact coordinates variant. */
    public static final String USAGE = "sls-manifest-oci-artifacts";

    /** Gradle artifact type used by OCI coordinates files. */
    public static final String COORDINATES_ARTIFACT_TYPE = "json";

    private ManifestOciArtifacts() {}
}
