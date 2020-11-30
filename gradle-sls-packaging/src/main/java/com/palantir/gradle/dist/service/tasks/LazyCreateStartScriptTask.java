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

package com.palantir.gradle.dist.service.tasks;

import javax.annotation.Nullable;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.application.CreateStartScripts;
import org.gradle.util.GradleVersion;

// TODO(forozco): Remove once we raise our minimum supported gradle version to 6.4
public class LazyCreateStartScriptTask extends CreateStartScripts {
    private final Property<String> mainClassName = getProject().getObjects().property(String.class);

    public LazyCreateStartScriptTask() {
        if (GradleVersion.current().compareTo(GradleVersion.version("6.4")) >= 0) {
            getMainClass().set(mainClassName);
        }
    }

    @Input
    public final Property<String> getLazyMainClassName() {
        return mainClassName;
    }

    @Nullable
    @Override
    public final String getMainClassName() {
        return getLazyMainClassName().get();
    }

    @Override
    public final void setMainClassName(@Nullable String _mainClassName) {
        throw new UnsupportedOperationException("Use lazy property instead");
    }
}
