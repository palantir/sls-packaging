/*
 * Copyright 2016 Palantir Technologies
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.gradle.dist.service

import com.palantir.gradle.dist.BaseDistributionExtension
import org.gradle.api.Project

class ServiceDistributionExtension extends BaseDistributionExtension {

    private String mainClass
    private List<String> args = []
    private List<String> checkArgs = []
    private List<String> defaultJvmOpts = []
    private Map<String, String> env = [:]
    private boolean enableManifestClasspath = false
    private String javaHome = null
    private List<String> excludeFromVar = ['log', 'run']

    ServiceDistributionExtension(Project project) {
        super(project)
        productType("service.v1")
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
}
