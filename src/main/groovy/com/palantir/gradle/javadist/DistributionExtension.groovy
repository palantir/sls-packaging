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
package com.palantir.gradle.javadist;

class DistributionExtension {

    private static final List<String> requiredJvmOpts = [
        '-Djava.security.egd=file:/dev/./urandom',
        '-Djava.io.tmpdir=var/data/tmp',
        '-XX:+PerfDisableSharedMem' // avoid memory-mapped IO during GC: http://www.evanjones.ca/jvm-mmap-pause.html
    ]

    private String serviceName
    private String mainClass
    private List<String> args = []
    private List<String> checkArgs = []
    private List<String> defaultJvmOpts = []
    private boolean enableManifestClasspath = false
    private String javaHome = null
    private List<String> excludeFromVar = []

    public void serviceName(String serviceName) {
        this.serviceName = serviceName
    }

    public void mainClass(String mainClass) {
        this.mainClass = mainClass
    }

    public void args(String... args) {
        this.args = Arrays.asList(args)
    }

    public void checkArgs(String... checkArgs) {
        this.checkArgs = Arrays.asList(checkArgs);
    }

    public void defaultJvmOpts(String... defaultJvmOpts) {
        this.defaultJvmOpts = Arrays.asList(defaultJvmOpts)
    }

    public void enableManifestClasspath(boolean enableManifestClasspath) {
        this.enableManifestClasspath = enableManifestClasspath
    }

    public void javaHome(String javaHome) {
        this.javaHome = javaHome
    }

    public void excludeFromVar(String... excludeFromVar) {
        this.excludeFromVar = Arrays.asList(excludeFromVar);
    }

    public String getServiceName() {
        return serviceName
    }

    public String getMainClass() {
        return mainClass
    }

    public List<String> getArgs() {
        return args
    }

    public List<String> getCheckArgs() {
        return checkArgs
    }

    public List<String> getDefaultJvmOpts() {
        return requiredJvmOpts + defaultJvmOpts
    }

    public boolean isEnableManifestClasspath() {
        return enableManifestClasspath
    }

    public String getJavaHome() {
        return javaHome
    }

    public List<String> getExcludeFromVar() {
        return excludeFromVar
    }
}
