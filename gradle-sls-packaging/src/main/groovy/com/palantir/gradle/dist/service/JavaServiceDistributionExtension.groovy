/*
 * (c) Copyright 2016 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.gradle.dist.service

import com.palantir.gradle.dist.BaseDistributionExtension
import com.palantir.gradle.dist.service.gc.GcProfile
import com.palantir.gradle.dist.service.gc.Hybrid
import com.palantir.gradle.dist.service.gc.ResponseTime
import com.palantir.gradle.dist.service.gc.Throughput
import groovy.transform.CompileStatic
import javax.annotation.Nullable
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.util.ConfigureUtil

@CompileStatic
class JavaServiceDistributionExtension extends BaseDistributionExtension {

    private static final Map<String, Class<? extends GcProfile>> profileNames = [
            "throughput": Throughput,
            "response-time": ResponseTime,
            "hybrid": Hybrid,
    ]

    private final ObjectFactory objects

    private String mainClass
    private GcProfile gc
    private boolean addJava8GCLogging = false
    private List<String> args = []
    private List<String> checkArgs = []
    private List<String> defaultJvmOpts = []
    private Map<String, String> env = [:]
    private boolean enableManifestClasspath = false
    private String javaHome = null
    private List<String> excludeFromVar = ['log', 'run']

    JavaServiceDistributionExtension(Project project) {
        super(project)
        productType("service.v1")
        objects = project.objects
        gc = new Throughput()
    }

    void mainClass(String mainClass) {
        this.mainClass = mainClass
    }

    void args(String... args) {
        this.args.addAll(args)
    }

    void setArgs(Iterable<String> args) {
        this.args = args.toList()
    }

    void checkArgs(String... checkArgs) {
        this.checkArgs.addAll(checkArgs)
    }

    void setCheckArgs(Iterable<String> checkArgs) {
        this.checkArgs = checkArgs.toList()
    }

    void defaultJvmOpts(String... defaultJvmOpts) {
        this.defaultJvmOpts.addAll(defaultJvmOpts)
    }

    void setDefaultJvmOpts(Iterable<String> defaultJvmOpts) {
        this.defaultJvmOpts = defaultJvmOpts.toList()
    }

    void enableManifestClasspath(boolean enableManifestClasspath) {
        this.enableManifestClasspath = enableManifestClasspath
    }

    void javaHome(String javaHome) {
        this.javaHome = javaHome
    }

    void setEnv(Map<String, String> env) {
        this.env = env
    }

    void env(Map<String, String> env) {
        this.env.putAll(env)
    }

    void excludeFromVar(String... excludeFromVar) {
        this.excludeFromVar.addAll(excludeFromVar)
    }

    void setExcludeFromVar(Iterable<String> excludeFromVar) {
        this.excludeFromVar = excludeFromVar.toList()
    }

    String getMainClass() {
        return mainClass
    }

    GcProfile getGc() {
        return gc
    }

    List<String> getArgs() {
        return args
    }

    List<String> getCheckArgs() {
        return checkArgs
    }

    List<String> getDefaultJvmOpts() {
        return defaultJvmOpts
    }

    boolean isEnableManifestClasspath() {
        return enableManifestClasspath
    }

    Map<String, String> getEnv() {
        return env
    }

    String getJavaHome() {
        return javaHome
    }

    List<String> getExcludeFromVar() {
        return excludeFromVar
    }

    void gc(String type, @Nullable Closure configuration) {
        gc = objects.<GcProfile>newInstance(profileNames[type])
        if (configuration != null) {
            ConfigureUtil.configure(configuration, gc)
        }
    }

    void gc(String type) {
        gc(type, null)
    }

    def <T extends GcProfile> void gc(Class<T> type, @Nullable Action<T> action) {
        // separate variable since 'gc' has type GcProfile and we need to give the action a 'T'
        def instance = objects.newInstance(type)
        gc = instance
        if (action != null) {
            action.execute(instance)
        }
    }

    def <T extends GcProfile> void gc(Class<T> type) {
        gc(type, null)
    }

    boolean getAddJava8GCLogging() {
        return addJava8GCLogging
    }

    void setAddJava8GCLogging(boolean addJava8GCLogging) {
        this.addJava8GCLogging = addJava8GCLogging
    }

    void addJava8GCLogging(boolean addJava8GCLogging) {
        this.addJava8GCLogging = addJava8GCLogging
    }
}
