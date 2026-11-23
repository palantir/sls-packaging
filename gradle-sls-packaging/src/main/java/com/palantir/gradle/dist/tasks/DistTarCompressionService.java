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

package com.palantir.gradle.dist.tasks;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * The gzip compression threads shared by every parallel-archiving {@link SlsDistTar} in a build, so that archiving several
 * distributions concurrently is bounded by {@code --max-workers} in total rather than per task.
 */
public abstract class DistTarCompressionService
        implements BuildService<DistTarCompressionService.Parameters>, AutoCloseable {
    // registerIfAbsent looks services up by name only, so a build where projects load this plugin through different
    // classloaders would otherwise get back a service of an incompatible class. A per-classloader suffix gives each
    // classloader its own service, as the Android Gradle Plugin does for its build services.
    private static final String NAME = "slsDistTarCompression-" + UUID.randomUUID();

    private ExecutorService executor;

    public interface Parameters extends BuildServiceParameters {
        Property<Integer> getThreads();
    }

    static Provider<DistTarCompressionService> register(Project project) {
        int maxWorkers = Math.max(1, project.getGradle().getStartParameter().getMaxWorkerCount());
        return project.getGradle()
                .getSharedServices()
                .registerIfAbsent(
                        NAME,
                        DistTarCompressionService.class,
                        spec -> spec.getParameters().getThreads().set(maxWorkers));
    }

    final int threads() {
        return getParameters().getThreads().get();
    }

    final synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newFixedThreadPool(threads(), daemonThreadFactory());
        }
        return executor;
    }

    @Override
    public final synchronized void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "sls-dist-tar-gzip-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
