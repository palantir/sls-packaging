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
package com.palantir.gradle.javadist

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.gradle.testkit.runner.BuildResult

class JavaDistributionPluginTests extends GradleTestSpec {

    def 'produce distribution bundle and check start, stop, restart, check behavior' () {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                checkArgs 'healthcheck'
            }
        '''.stripIndent()

        file('src/main/java/test/Test.java') << '''
        package test;
        public class Test {
            public static void main(String[] args) throws InterruptedException {
                if (args.length > 0 && args[0].equals("healthcheck")) System.exit(0); // always healthy

                System.out.println("Test started");
                while(true);
            }
        }
        '''.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        // try all of the service commands
        exec('dist/service-name-0.1/service/bin/init.sh', 'start') ==~ /(?m)Running 'service-name'\.\.\.\s+Started \(\d+\)\n/
        file('dist/service-name-0.1/var/log/service-name-startup.log', projectDir).text.contains('Test started\n')
        exec('dist/service-name-0.1/service/bin/init.sh', 'status') ==~ /(?m)Checking 'service-name'\.\.\.\s+Running \(\d+\)\n/
        exec('dist/service-name-0.1/service/bin/init.sh', 'restart') ==~
            /(?m)Stopping 'service-name'\.\.\.\s+Stopped \(\d+\)\nRunning 'service-name'\.\.\.\s+Started \(\d+\)\n/
        exec('dist/service-name-0.1/service/bin/init.sh', 'stop') ==~ /(?m)Stopping 'service-name'\.\.\.\s+Stopped \(\d+\)\n/
        exec('dist/service-name-0.1/service/bin/init.sh', 'check') ==~ /(?m)Checking health of 'service-name'\.\.\.\s+Healthy.*\n/
        exec('dist/service-name-0.1/service/monitoring/bin/check.sh') ==~ /(?m)Checking health of 'service-name'\.\.\.\s+Healthy.*\n/

        // check manifest was created
        String manifest = file('dist/service-name-0.1/deployment/manifest.yml', projectDir).text
        manifest.contains('productName: service-name\n')
        manifest.contains('productVersion: 0.1\n')
    }

    def 'produce distribution bundle and check var/log and var/run are excluded' () {
        given:
        createUntarBuildFile(buildFile)

        createFile('var/log/service-name.log')
        createFile('var/run/service-name.pid')
        createFile('var/conf/service-name.yml')

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        file('dist/service-name-0.1').exists()
        !file('dist/service-name-0.1/var/log').exists()
        !file('dist/service-name-0.1/var/run').exists()
        file('dist/service-name-0.1/var/conf/service-name.yml').exists()
    }

    def 'produce distribution bundle and check var/data/tmp is created' () {
        given:
        createUntarBuildFile(buildFile)

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        file('dist/service-name-0.1/var/data/tmp').exists()
    }

    def 'produce distribution bundle with custom exclude set' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'java'
            }

            version '0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                defaultJvmOpts '-Xmx4M', '-Djavax.net.ssl.trustStore=truststore.jks'
                excludeFromVar 'data'
            }

            sourceCompatibility = '1.7'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/service-name-0.1.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()

        createFile('var/log/service-name.log')
        createFile('var/data/database')
        createFile('var/conf/service-name.yml')

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        !file('dist/service-name-0.1/var/log').exists()
        !file('dist/service-name-0.1/var/data').exists()
        file('dist/service-name-0.1/var/conf/service-name.yml').exists()
    }

    def 'produce distribution bundle with a non-string version object' () {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'java'
            }

            class MyVersion {
                String version

                MyVersion(String version) {
                    this.version = version
                }

                String toString() {
                    return this.version
                }
            }

            version new MyVersion('0.1')

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
            }

            sourceCompatibility = '1.7'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/service-name-0.1.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        String manifest = file('dist/service-name-0.1/deployment/manifest.yml', projectDir).text
        manifest.contains('productName: service-name\n')
        manifest.contains('productVersion: 0.1\n')
    }

    def 'produce distribution bundle with files in deployment/' () {
        given:
        createUntarBuildFile(buildFile)

        String deploymentConfiguration = 'log: service-name.log'
        createFile('deployment/manifest.yml') << 'invalid manifest'
        createFile('deployment/configuration.yml') << deploymentConfiguration

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        // clobbers deployment/manifest.yml
        String manifest = file('dist/service-name-0.1/deployment/manifest.yml', projectDir).text
        manifest.contains('productName: service-name\n')
        manifest.contains('productVersion: 0.1\n')

        // check files in deployment/ copied successfully
        String actualConfiguration = file('dist/service-name-0.1/deployment/configuration.yml', projectDir).text
        actualConfiguration.equals(deploymentConfiguration)
    }

    def 'produce distribution bundle with start script that passes default JVM options' () {
        given:
        createUntarBuildFile(buildFile)

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        String startScript = file('dist/service-name-0.1/service/bin/service-name', projectDir).text
        startScript.contains('DEFAULT_JVM_OPTS=\'"-Djava.security.egd=file:/dev/./urandom" "-Djava.io.tmpdir=var/data/tmp" "-Xmx4M" "-Djavax.net.ssl.trustStore=truststore.jks"\'')
    }

    def 'produce distribution bundle that populates launcher.yml and launcher-check.yml' () {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            dependencies { compile files("external.jar") }
            tasks.jar.baseName = "internal"
            distribution {
                javaHome 'foo'
                args 'myArg1', 'myArg2'
                checkArgs 'myCheckArg1', 'myCheckArg2'
            }'''.stripIndent()
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        def expectedLaunchConfig = new LaunchConfigTask.LaunchConfig()
        expectedLaunchConfig.setConfigVersion(1)
        expectedLaunchConfig.setConfigType("java")
        expectedLaunchConfig.setServiceName("service-name")
        expectedLaunchConfig.setMainClass("test.Test")
        expectedLaunchConfig.setJavaHome("foo")
        expectedLaunchConfig.setArgs(['myArg1', 'myArg2'])
        expectedLaunchConfig.setClasspath(['service/lib/internal-0.1.jar', 'service/lib/external.jar'])
        expectedLaunchConfig.setJvmOpts([
                '-Djava.security.egd=file:/dev/./urandom',
                '-Djava.io.tmpdir=var/data/tmp',
                '-Xmx4M',
                '-Djavax.net.ssl.trustStore=truststore.jks'])
        def actualLaunchConfig = new ObjectMapper(new YAMLFactory()).readValue(
                file('dist/service-name-0.1/var/launch/launcher.yml'), LaunchConfigTask.LaunchConfig)
        expectedLaunchConfig == actualLaunchConfig

        def expectedCheckConfig = expectedLaunchConfig
        expectedCheckConfig.setArgs(['myCheckArg1', 'myCheckArg2'])
        def actualCheckConfig = new ObjectMapper(new YAMLFactory()).readValue(
                file('dist/service-name-0.1/var/launch/launcher-check.yml'), LaunchConfigTask.LaunchConfig)
        expectedCheckConfig == actualCheckConfig
    }

    def 'produce distribution bundle that populates check.sh' () {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                checkArgs 'healthcheck', 'var/conf/service.yml'
            }
        '''.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        file('dist/service-name-0.1/service/monitoring/bin/check.sh').exists()
    }

    def 'produces manifest-classpath jar and windows start script with no classpath length limitations' () {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                enableManifestClasspath true
            }
        '''.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        String startScript = file('dist/service-name-0.1/service/bin/service-name.bat', projectDir).text
        startScript.contains("-manifest-classpath-0.1.jar")
        !startScript.contains("-classpath \"%CLASSPATH%\"")
        file('dist/service-name-0.1/service/lib/').listFiles()
            .find({it.name.endsWith("-manifest-classpath-0.1.jar")})
    }

    def 'does not produce manifest-classpath jar when disabled in extension'() {
        given:
        createUntarBuildFile(buildFile)

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        String startScript = file('dist/service-name-0.1/service/bin/service-name.bat', projectDir).text
        !startScript.contains("-manifest-classpath-0.1.jar")
        startScript.contains("-classpath \"%CLASSPATH%\"")
        !new File(projectDir, 'dist/service-name-0.1/service/lib/').listFiles()
            .find({it.name.endsWith("-manifest-classpath-0.1.jar")})
    }

    def 'distTar artifact name is set during appropriate lifecycle events'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'java'
            }
            distribution {
                serviceName "my-service"
                mainClass "dummy.service.MainClass"
                args "hello"
            }

            println "before: distTar: ${distTar.outputs.files.singleFile}"

            afterEvaluate {
                println "after: distTar: ${distTar.outputs.files.singleFile}"
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = runSuccessfully(':tasks')

        then:
        buildResult.output =~ ("before: distTar: ${projectDir}/build/distributions/my-service.tgz")
        buildResult.output =~ ("after: distTar: ${projectDir}/build/distributions/my-service.tgz")

    }

    private static def createUntarBuildFile(buildFile) {
        buildFile << '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'java'
            }

            version '0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                defaultJvmOpts '-Xmx4M', '-Djavax.net.ssl.trustStore=truststore.jks'
            }

            sourceCompatibility = '1.7'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/service-name-0.1.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()
    }
}
