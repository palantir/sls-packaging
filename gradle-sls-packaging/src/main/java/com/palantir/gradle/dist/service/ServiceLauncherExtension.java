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

package com.palantir.gradle.dist.service;

import com.palantir.gradle.dist.service.tasks.CreateInitScript;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

/**
 * Configures which launcher runs the service inside the distribution.
 *
 * <p>By default the distribution ships the go-java-launcher and go-init binaries plus the {@code init.sh} template
 * bundled with this plugin. Another plugin (or a repo) can swap either piece out:
 *
 * <pre>
 * distribution {
 *     launcher {
 *         useDefaultBinaries = false
 *         binaries {
 *             from(myLauncherConfiguration) { into 'my-launcher' }
 *         }
 *         initScriptTemplateFile = file('src/dist/my-init.sh')
 *         initScriptVars = ['@launcherDir@': 'my-launcher']
 *     }
 * }
 * </pre>
 *
 * <p>Anything contributed to {@link #getBinaries()} is copied into {@code service/bin} of the distribution.
 */
public abstract class ServiceLauncherExtension {

    private final CopySpec binaries;
    private final ProviderFactory providers;
    private final Project project;

    @Inject
    public ServiceLauncherExtension(Project project) {
        this.project = project;
        this.providers = project.getProviders();
        this.binaries = project.copySpec();

        getUseDefaultBinaries().convention(true);
        getInitScriptTemplate().convention(project.provider(CreateInitScript::defaultTemplate));
    }

    /**
     * Whether to unpack the go-java-launcher and go-init binaries into the distribution. Set to false when replacing
     * them with launcher binaries contributed via {@link #getBinaries()}.
     */
    public abstract Property<Boolean> getUseDefaultBinaries();

    /** Contents of the init script to generate, defaulting to the {@code init.sh} shipped with this plugin. */
    public abstract Property<String> getInitScriptTemplate();

    /**
     * Additional placeholder replacements applied to {@link #getInitScriptTemplate()}. {@code @serviceName@} is always
     * replaced with the distribution service name.
     */
    public abstract MapProperty<String, String> getInitScriptVars();

    /** Extra files to place in {@code service/bin} of the distribution, e.g. custom launcher binaries. */
    public final CopySpec getBinaries() {
        return binaries;
    }

    public final void binaries(Action<? super CopySpec> action) {
        action.execute(binaries);
    }

    public final void setInitScriptTemplateFile(Object file) {
        initScriptTemplateFile(project.getLayout().file(project.provider(() -> project.file(file))));
    }

    public final void initScriptTemplateFile(Provider<RegularFile> file) {
        getInitScriptTemplate().set(providers.fileContents(file).getAsText());
    }
}
