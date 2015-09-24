/*
 * Copyright 2015 Palantir Technologies
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

    private String serviceName
    private String mainClass
    private List<String> args

    public void serviceName(String serviceName) {
        this.serviceName = serviceName
    }

    public void mainClass(String mainClass) {
        this.mainClass = mainClass
    }

    public List<String> args(String... args) {
        this.args = Arrays.asList(args)
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getMainClass() {
        return mainClass;
    }

    public List<String> getArgs() {
        return args;
    }

}