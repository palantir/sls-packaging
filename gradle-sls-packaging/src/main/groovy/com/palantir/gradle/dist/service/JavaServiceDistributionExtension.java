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

import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.dist.BaseDistributionExtension;
import com.palantir.gradle.dist.ProductType;
import com.palantir.gradle.dist.service.gc.GcProfile;
import com.palantir.gradle.dist.service.gc.Hybrid;
import com.palantir.gradle.dist.service.gc.ResponseTime;
import com.palantir.gradle.dist.service.gc.ResponseTime11;
import com.palantir.gradle.dist.service.gc.Throughput;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

public class JavaServiceDistributionExtension extends BaseDistributionExtension {

    private static final Map<String, Class<? extends GcProfile>> profileNames = ImmutableMap.of(
            "throughput", Throughput.class,
            "response-time", ResponseTime.class,
            "hybrid", Hybrid.class,
            "response-time-11", ResponseTime11.class);

    private Property<String> mainClass;
    private Property<String> javaHome;
    private Property<Boolean> addJava8GcLogging;
    private Property<Boolean> enableManifestClasspath;
    private Property<GcProfile> gc;
    private ListProperty<String> args;
    private ListProperty<String> checkArgs;
    private ListProperty<String> defaultJvmOpts;
    private ListProperty<String> excludeFromVar;
    private MapProperty<String, String> env;

    private ObjectFactory objectFactory;

    @Inject
    public JavaServiceDistributionExtension(Project project, ObjectFactory objectFactory) {
        super(project, objectFactory);

        this.objectFactory = objectFactory;
        mainClass = objectFactory.property(String.class);
        javaHome = objectFactory.property(String.class);
        addJava8GcLogging = objectFactory.property(Boolean.class).value(false);
        enableManifestClasspath = objectFactory.property(Boolean.class).value(false);
        gc = objectFactory.property(GcProfile.class).value(new Throughput());
        args = objectFactory.listProperty(String.class).empty();
        checkArgs = objectFactory.listProperty(String.class).empty();
        defaultJvmOpts = objectFactory.listProperty(String.class).empty();
        excludeFromVar = objectFactory.listProperty(String.class).empty();
        excludeFromVar.addAll("log", "run");
        env = objectFactory.mapProperty(String.class, String.class).empty();

        setProductType(ProductType.SERVICE_V1);
    }

    public final Provider<String> getMainClass() {
        return mainClass;
    }

    public final void setMainClass(String mainClass) {
        this.mainClass.set(mainClass);
    }

    public final Provider<String> getJavaHome() {
        return javaHome;
    }

    public final void setJavaHome(String javaHome) {
        this.javaHome.set(javaHome);
    }

    public final Provider<Boolean> getAddJava8GcLogging() {
        return addJava8GcLogging;
    }

    public final void setAddJava8GcLogging(boolean addJava8GcLogging) {
        this.addJava8GcLogging.set(addJava8GcLogging);
    }

    public final Provider<Boolean> getEnableManifestClasspath() {
        return enableManifestClasspath;
    }

    public final void setEnableManifestClasspath(boolean enableManifestClasspath) {
        this.enableManifestClasspath.set(enableManifestClasspath);
    }

    public final Provider<List<String>> getArgs() {
        return args;
    }

    public final void args(String... newArgs) {
        this.args.addAll(newArgs);
    }

    public final void setArgs(Iterable<String> args) {
        this.args.set(args);
    }

    public final Provider<List<String>> getCheckArgs() {
        return checkArgs;
    }

    public final void checkArgs(String... newCheckArgs) {
        this.checkArgs.addAll(newCheckArgs);
    }

    public final void setCheckArgs(Iterable<String> checkArgs) {
        this.checkArgs.set(checkArgs);
    }

    public final Provider<List<String>> getDefaultJvmOpts() {
        return defaultJvmOpts;
    }

    public final void defaultJvmOpts(String... opts) {
        this.defaultJvmOpts.addAll(opts);
    }

    public final void setDefaultJvmOpts(Iterable<String> defaultJvmOpts) {
        this.defaultJvmOpts.set(defaultJvmOpts);
    }

    public final Provider<List<String>> getExcludeFromVar() {
        return excludeFromVar;
    }

    public final void excludeFromVar(String... fromVar) {
        this.excludeFromVar.addAll(fromVar);
    }

    public final void setExcludeFromVar(Iterable<String> excludeFromVar) {
        this.excludeFromVar.set(excludeFromVar);
    }

    public final Provider<Map<String, String>> getEnv() {
        return env;
    }

    public final void env(Map<String, String> newEnv) {
        this.env.putAll(newEnv);
    }

    public final void setEnv(Map<String, String> env) {
        this.env.set(env);
    }

    public final Provider<GcProfile> getGc() {
        return gc;
    }

    public final void setGc(String type, @Nullable @DelegatesTo(GcProfile.class) Closure configuration) {
        GcProfile newGc = objectFactory.<GcProfile>newInstance(profileNames.get(type));
        if (configuration != null) {
            configuration.setDelegate(gc);
            configuration.call();
        }
        gc.set(newGc);
    }

    public final void gc(String type) {
        setGc(type, null);
    }

    // public void <T extends GcProfile> gc(Class<T> type, @Nullable Action<T> action) {
    //     // separate variable since 'gc' has type GcProfile and we need to give the action a 'T'
    //     T instance = objectFactory.newInstance(type);
    //     gc = instance;
    //     if (action != null) {
    //         action.execute(instance);
    //     }
    // }
    //
    // void <T extends GcProfile> gc(Class<T> type) {
    //     setGc(type, null);
    // }
}
