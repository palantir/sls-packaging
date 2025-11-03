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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.palantir.gradle.dist.GradleIntegrationSpec
import com.palantir.gradle.dist.GradleTestVersions
import com.palantir.gradle.dist.SlsManifest
import com.palantir.gradle.dist.Versions
import com.palantir.gradle.dist.service.tasks.LaunchConfig
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert
import spock.lang.Unroll

import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class JavaServiceDistributionPluginTests extends GradleIntegrationSpec {
    private static final OBJECT_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new GuavaModule())

    private static final String EXTERNAL_JAR = new File("src/test/resources/external.jar").getAbsolutePath();

    def '#gradleVersionNumber: produce distribution bundle and check start, stop, restart, check behavior'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                checkArgs 'healthcheck'
            }
        '''.stripIndent(true)
        file('var/conf/launcher-custom.yml') << '''
            configType: java
            configVersion: 1
            jvmOpts:
              - '-Dcustom.property=myCustomValue'
        '''.stripIndent(true)

        file('src/main/java/test/Test.java') << '''
        package test;
        import java.lang.IllegalStateException;
        public class Test {
            public static void main(String[] args) throws InterruptedException {
                if (args.length > 0 && args[0].equals("healthcheck")) System.exit(0); // always healthy

                if (!System.getProperty("custom.property").equals("myCustomValue")) {
                    throw new IllegalStateException("Expected custom.start.property to be set");
                }
                System.out.println("Test started");
                while(true);
            }
        }
        '''.stripIndent(true)

        when:
        def result = runTasks(':build', ':distTar', ':untar')

        then:
        result.task(':createCheckScript').outcome == TaskOutcome.SUCCESS
        // try all of the service commands
        execWithExitCode('dist/service-name-0.0.1/service/bin/init.sh', 'start') == 0
        // wait for the Java process to start up and emit output
        sleep 1000
        file('dist/service-name-0.0.1/var/log/startup.log', projectDir).text.contains('Test started\n')
        execWithExitCode('dist/service-name-0.0.1/service/bin/init.sh', 'start') == 0
        execWithExitCode('dist/service-name-0.0.1/service/bin/init.sh', 'status') == 0
        execWithExitCode('dist/service-name-0.0.1/service/bin/init.sh', 'restart') == 0
        execWithExitCode('dist/service-name-0.0.1/service/bin/init.sh', 'stop') == 0
        execWithOutput('dist/service-name-0.0.1/service/bin/init.sh', 'check').readLines().any {
            it ==~ /Checking health of 'service-name'\.\.\.\s+Healthy/
        }
        execWithOutput('dist/service-name-0.0.1/service/monitoring/bin/check.sh').readLines().any {
            it ==~ /Checking health of 'service-name'\.\.\.\s+Healthy/
        }

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: packaging tasks re-run after version change'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                enableManifestClasspath true
            }
         '''.stripIndent(true)
        file('src/main/java/test/Test.java') << '''
        package test;
        public class Test {
            public static void main(String[] args) {
                while(true);
            }
        }
        '''.stripIndent(true)

        when:
        runTasks(':build', ':distTar', ':untar')
        buildFile << '''
            version '0.0.2'
        '''.stripIndent(true)

        then:
        def result = runTasks(':build', ':distTar', ':untar')
        result.task(':createCheckScript').outcome == TaskOutcome.UP_TO_DATE
        result.task(':createInitScript').outcome == TaskOutcome.UP_TO_DATE
        result.task(':createLaunchConfig').outcome == TaskOutcome.SUCCESS
        result.task(':createManifest').outcome == TaskOutcome.SUCCESS
        result.task(':manifestClasspathJar').outcome == TaskOutcome.SUCCESS
        result.task(':distTar').outcome == TaskOutcome.SUCCESS

        execWithExitCode('dist/service-name-0.0.2/service/bin/init.sh', 'start') == 0
        execWithExitCode('dist/service-name-0.0.2/service/bin/init.sh', 'stop') == 0

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution bundle and check var/log and var/run are excluded'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)

        createFile('var/log/service-name.log')
        createFile('var/run/service-name.pid')
        createFile('var/conf/service-name.yml')

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        fileExists('dist/service-name-0.0.1')
        !fileExists('dist/service-name-0.0.1/var/log')
        !fileExists('dist/service-name-0.0.1/var/run')
        fileExists('dist/service-name-0.0.1/var/conf/service-name.yml')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution bundle and check var/data/tmp is created and used for temporary files'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        file('src/main/java/test/Test.java') << '''
        package test;
        import java.nio.file.Files;
        import java.io.IOException;
        public class Test {
            public static void main(String[] args) throws IOException {
                Files.write(Files.createTempFile("prefix", "suffix"), "temp content".getBytes());
            }
        }
        '''.stripIndent(true)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        execAllowFail('dist/service-name-0.0.1/service/bin/init.sh', 'start')
        sleep(1000)
        file('dist/service-name-0.0.1/var/data/tmp').listFiles().length == 1
        file('dist/service-name-0.0.1/var/data/tmp').listFiles()[0].text == "temp content"

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution bundle with custom exclude set'() {
        given:
        gradleVersion = gradleVersionNumber
        buildFile << '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                mavenCentral()
            }

            version '0.0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                defaultJvmOpts '-Xmx4M', '-Djavax.net.ssl.trustStore=truststore.jks'
                excludeFromVar 'data'
            }

            java {
                sourceCompatibility = '1.7'
            }
        '''.stripIndent(true)

        createUntarTask(buildFile)

        createFile('var/log/service-name.log')
        createFile('var/data/database')
        createFile('var/conf/service-name.yml')

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        !fileExists('dist/service-name-0.0.1/var/log')
        !fileExists('dist/service-name-0.0.1/var/data/database')
        fileExists('dist/service-name-0.0.1/var/conf/service-name.yml')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution bundle with a non-string version object'() {
        given:
        gradleVersion = gradleVersionNumber
        buildFile << '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                mavenCentral()
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

            version new MyVersion('0.0.1')

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
            }

            java {
                sourceCompatibility = '1.7'
            }
        '''.stripIndent(true)

        createUntarTask(buildFile)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def manifest = OBJECT_MAPPER.readValue(file('dist/service-name-0.0.1/deployment/manifest.yml'), SlsManifest);
        manifest.productVersion() == "0.0.1"

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: manifest file contains expected fields'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def manifest = OBJECT_MAPPER.readValue(file('dist/service-name-0.0.1/deployment/manifest.yml'), Map)
        manifest.get("manifest-version") == "1.0"
        manifest.get("product-group") == "service-group"
        manifest.get("product-name") == "service-name"
        manifest.get("product-version") == "0.0.1"
        manifest.get("product-type") == "service.v1"
        manifest.get("extensions").get("foo") == ["bar": ["1", "2"]]

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: can specify service dependencies'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            distribution {
                productDependency {
                    productGroup = "group1"
                    productName = "name1"
                    minimumVersion = "1.0.0"
                    maximumVersion = "1.3.x"
                    recommendedVersion = "1.2.1"
                }
                productDependency {
                    productGroup = "group2"
                    productName = "name2"
                    minimumVersion = "1.0.0"
                    maximumVersion = "1.x.x"
                }
            }
        """.stripIndent(true)
        file('product-dependencies.lock').text = """\
        # Run ./gradlew writeProductDependenciesLocks to regenerate this file
        group1:name1 (1.0.0, 1.3.x)
        group2:name2 (1.0.0, 1.x.x)
        """.stripIndent(true)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def mapper = new ObjectMapper()
        def manifest = mapper.readValue(file('dist/service-name-0.0.1/deployment/manifest.yml', projectDir), Map)

        def dep1 = manifest['extensions']['product-dependencies'][0]
        dep1['product-group'] == 'group1'
        dep1['product-name'] == 'name1'
        dep1['minimum-version'] == '1.0.0'
        dep1['maximum-version'] == '1.3.x'
        dep1['recommended-version'] == "1.2.1"

        def dep2 = manifest['extensions']['product-dependencies'][1]
        dep2['product-group'] == 'group2'
        dep2['product-name'] == 'name2'
        dep2['minimum-version'] == '1.0.0'

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: cannot specify service dependencies with invalid versions, with closure constructor'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            distribution {
                productDependency {
                    productName = "name1"
                    productGroup = "group1"
                    minimumVersion = "1.0.x"
                    maximumVersion = "2.0.0"
                }
            }
        """.stripIndent(true)

        when:
        def result = runTasksAndFail('distTar')

        then:
        result.output.contains("minimumVersion must be an SLS version")

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution bundle with files in deployment/'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)

        String deploymentConfiguration = 'log: service-name.log'
        createFile('deployment/manifest.yml') << 'invalid manifest'
        createFile('deployment/configuration.yml') << deploymentConfiguration

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        // clobbers deployment/manifest.yml
        def manifest = OBJECT_MAPPER.readValue(file('dist/service-name-0.0.1/deployment/manifest.yml'), SlsManifest)
        manifest.productName() == "service-name"

        // check files in deployment/ copied successfully
        String actualConfiguration = new File(projectDir, 'dist/service-name-0.0.1/deployment/configuration.yml').text
        actualConfiguration == deploymentConfiguration

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: allows another task to produce configuration.yml'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        debug = true

        // language=Gradle
        buildFile << '''
            task createConfigurationYml {
                outputs.file('build/some-place/configuration.yml')

                doFirst {
                    file('build/some-place/configuration.yml').text = 'custom: yml'
                }
            }

            distribution {
                configurationYml.fileProvider(tasks.named('createConfigurationYml').map { it.outputs.files.singleFile })
            }
        '''.stripIndent(true)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        String actualConfiguration = new File(projectDir, 'dist/service-name-0.0.1/deployment/configuration.yml').text
        actualConfiguration == 'custom: yml'

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: errors out if the custom configuration.yml location is not a file called configuration.yml'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        debug = true

        // language=Gradle
        buildFile << '''
            task createConfigurationYml {
                outputs.file('build/some-place/something-else.yml')

                doFirst {
                    file('build/some-place/something-else.yml').text = 'custom: yml'
                }
            }

            distribution {
                configurationYml.fileProvider(tasks.named('createConfigurationYml').map { it.outputs.files.singleFile })
            }
        '''.stripIndent(true)

        when:
        def output = runTasksAndFail(':build', ':distTar', ':untar').output

        then:
        output.contains('must be called configuration.yml')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution bundle with start script that passes default JVM options'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        String startScript = new File(projectDir, 'dist/service-name-0.0.1/service/bin/service-name').text
        startScript.contains('DEFAULT_JVM_OPTS=\'"-Xmx4M" "-Djavax.net.ssl.trustStore=truststore.jks"\'')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution bundle that populates launcher-static.yml and launcher-check.yml'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)

        buildFile << """
            dependencies { implementation files("${EXTERNAL_JAR}") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
                javaHome 'foo'
                args 'myArg1', 'myArg2'
                checkArgs 'myCheckArg1', 'myCheckArg2'
                env "key1": "val1",
                    "key2": "val2"
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def expectedStaticConfig = LaunchConfig.LaunchConfigInfo.builder()
                .mainClass("test.Test")
                .serviceName("service-name")
                .javaHome("foo")
                .args(["myArg1", "myArg2"])
                .classpath(['service/lib/internal-0.0.1.jar', 'service/lib/external.jar'])
                .jvmOpts([
                        '-XX:+CrashOnOutOfMemoryError',
                        '-Djava.io.tmpdir=var/data/tmp',
                        '-Djna.tmpdir=var/data/tmp',
                        '-XX:ErrorFile=var/log/hs_err_pid%p.log',
                        '-XX:HeapDumpPath=var/log',
                        '-Dsun.net.inetaddr.ttl=10',
                        '-XX:+UnlockDiagnosticVMOptions',
                        '-XX:+IgnoreUnrecognizedVMOptions',
                        '-XX:NativeMemoryTracking=summary',
                        '-XX:FlightRecorderOptions=stackdepth=256',
                        '-XX:UseAVX=2',
                        '-XX:CompileCommand=exclude,sun/security/ssl/SSLEngineInputRecord.decodeInputRecord',
                        '-XX:-UseBiasedLocking',
                        '-XX:+UseParallelGC',
                        '-Xmx4M',
                        '-Djavax.net.ssl.trustStore=truststore.jks'])
                .env(LaunchConfig.defaultEnvironment + [
                        "key1": "val1",
                        "key2": "val2"])
                .dirs(["var/data/tmp"])
                .build()
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)

        def expectedCheckConfig = LaunchConfig.LaunchConfigInfo.builder()
                .mainClass(actualStaticConfig.mainClass())
                .serviceName(actualStaticConfig.serviceName())
                .javaHome(actualStaticConfig.javaHome())
                .args(["myCheckArg1", "myCheckArg2"])
                .classpath(actualStaticConfig.classpath())
                .jvmOpts([
                        '-XX:+CrashOnOutOfMemoryError',
                        '-Djava.io.tmpdir=var/data/tmp',
                        '-Djna.tmpdir=var/data/tmp',
                        '-XX:ErrorFile=var/log/hs_err_pid%p.log',
                        '-XX:HeapDumpPath=var/log',
                        '-Dsun.net.inetaddr.ttl=10',
                        '-XX:+UnlockDiagnosticVMOptions',
                        '-XX:+IgnoreUnrecognizedVMOptions',
                        '-XX:NativeMemoryTracking=summary',
                        '-XX:FlightRecorderOptions=stackdepth=256',
                        '-XX:UseAVX=2',
                        '-Xmx4M',
                        '-Djavax.net.ssl.trustStore=truststore.jks'])
                .env(LaunchConfig.defaultEnvironment)
                .dirs(actualStaticConfig.dirs())
                .build()

        def actualCheckConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-check.yml'), LaunchConfig.LaunchConfigInfo)
        expectedCheckConfig == actualCheckConfig
        expectedStaticConfig == actualStaticConfig

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }


    def '#gradleVersionNumber: produce distribution bundle that populates launcher-static.yml and launcher-check.yml with bundled jdk'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)

        buildFile << """
            dependencies { implementation files("${EXTERNAL_JAR}") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
                jdks.put(JavaVersion.VERSION_11, fileTree('build/fake-jdk'))
                javaHome 'foo'
                args 'myArg1', 'myArg2'
                checkArgs 'myCheckArg1', 'myCheckArg2'
                env "key1": "val1",
                    "key2": "val2"
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def expectedStaticConfig = LaunchConfig.LaunchConfigInfo.builder()
                .mainClass("test.Test")
                .serviceName("service-name")
                .javaHome("foo")
                .args(["myArg1", "myArg2"])
                .classpath(['service/lib/internal-0.0.1.jar', 'service/lib/external.jar'])
                .jvmOpts([
                        '-XX:+CrashOnOutOfMemoryError',
                        '-Djava.io.tmpdir=var/data/tmp',
                        '-Djna.tmpdir=var/data/tmp',
                        '-XX:ErrorFile=var/log/hs_err_pid%p.log',
                        '-XX:HeapDumpPath=var/log',
                        '-Dsun.net.inetaddr.ttl=10',
                        '-XX:+UnlockDiagnosticVMOptions',
                        '-XX:+IgnoreUnrecognizedVMOptions',
                        '-XX:NativeMemoryTracking=summary',
                        '-XX:FlightRecorderOptions=stackdepth=256',
                        '-XX:UseAVX=2',
                        '-XX:-UseBiasedLocking',
                        '-XX:+UseParallelGC',
                        '-Xmx4M',
                        '-Djavax.net.ssl.trustStore=truststore.jks'])
                .env(LaunchConfig.defaultEnvironment + [
                        "key1": "val1",
                        "key2": "val2",
                        "JAVA_11_HOME": "service/service-name-jdks/jdk11"])
                .dirs(["var/data/tmp"])
                .build()
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                file('dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)

        def expectedCheckConfig = LaunchConfig.LaunchConfigInfo.builder()
                .mainClass(actualStaticConfig.mainClass())
                .serviceName(actualStaticConfig.serviceName())
                .javaHome(actualStaticConfig.javaHome())
                .args(["myCheckArg1", "myCheckArg2"])
                .classpath(actualStaticConfig.classpath())
                .jvmOpts([
                        '-XX:+CrashOnOutOfMemoryError',
                        '-Djava.io.tmpdir=var/data/tmp',
                        '-Djna.tmpdir=var/data/tmp',
                        '-XX:ErrorFile=var/log/hs_err_pid%p.log',
                        '-XX:HeapDumpPath=var/log',
                        '-Dsun.net.inetaddr.ttl=10',
                        '-XX:+UnlockDiagnosticVMOptions',
                        '-XX:+IgnoreUnrecognizedVMOptions',
                        '-XX:NativeMemoryTracking=summary',
                        '-XX:FlightRecorderOptions=stackdepth=256',
                        '-XX:UseAVX=2',
                        '-Xmx4M',
                        '-Djavax.net.ssl.trustStore=truststore.jks'])
                .env(LaunchConfig.defaultEnvironment)
                .dirs(actualStaticConfig.dirs())
                .build()

        def actualCheckConfig = OBJECT_MAPPER.readValue(
                file('dist/service-name-0.0.1/service/bin/launcher-check.yml'), LaunchConfig.LaunchConfigInfo)
        expectedCheckConfig == actualCheckConfig
        expectedStaticConfig == actualStaticConfig

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution with java 8 gc logging'() {
        setup:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies { implementation files("${EXTERNAL_JAR}") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaHome 'foo'
                addJava8GcLogging true
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def expectedStaticConfig = LaunchConfig.LaunchConfigInfo.builder()
            .mainClass("test.Test")
            .serviceName("service-name")
            .javaHome("foo")
            .classpath(['service/lib/internal-0.0.1.jar', 'service/lib/external.jar'])
            .jvmOpts([
                '-XX:+CrashOnOutOfMemoryError',
                '-Djava.io.tmpdir=var/data/tmp',
                '-Djna.tmpdir=var/data/tmp',
                '-XX:ErrorFile=var/log/hs_err_pid%p.log',
                '-XX:HeapDumpPath=var/log',
                '-Dsun.net.inetaddr.ttl=10',
                '-XX:+UnlockDiagnosticVMOptions',
                '-XX:+IgnoreUnrecognizedVMOptions',
                '-XX:NativeMemoryTracking=summary',
                '-XX:FlightRecorderOptions=stackdepth=256',
                '-XX:UseAVX=2',
                "-XX:+PrintGCDateStamps",
                "-XX:+PrintGCDetails",
                "-XX:-TraceClassUnloading",
                "-XX:+UseGCLogFileRotation",
                "-XX:GCLogFileSize=10M",
                "-XX:NumberOfGCLogFiles=10",
                "-Xloggc:var/log/gc-%t-%p.log",
                "-verbose:gc",
                "-XX:-UseBiasedLocking",
                '-XX:+UseParallelGC',
                '-Xmx4M',
                '-Djavax.net.ssl.trustStore=truststore.jks'])
            .dirs(["var/data/tmp"])
            .env(["MALLOC_ARENA_MAX": '4'])
            .build()
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        expectedStaticConfig == actualStaticConfig

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: respects java version'() {
        setup:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies { implementation files("${EXTERNAL_JAR}") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 14
                gc 'response-time'
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        actualStaticConfig.jvmOpts().containsAll([
                "-XX:+UseShenandoahGC",
                "-XX:+ExplicitGCInvokesConcurrent",
                "-XX:+ClassUnloadingWithConcurrentMark",
        ])

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: Uses generational zgc for jdk-21'() {
        setup:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies { implementation files("${EXTERNAL_JAR}") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 21
                gc 'response-time'
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        actualStaticConfig.jvmOpts().containsAll([
                "-XX:+UseZGC",
                "-XX:+ZGenerational",
                "-XX:+ExplicitGCInvokesConcurrent",
        ])

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: jdk-21 uses default AVX level'() {
        setup:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies { implementation files("${EXTERNAL_JAR}") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 21
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        actualStaticConfig.jvmOpts().stream().noneMatch { it.contains("UseAVX") }

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution bundle that populates check.sh'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                checkArgs 'healthcheck', 'var/conf/service.yml'
            }
        '''.stripIndent(true)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        new File(projectDir, 'dist/service-name-0.0.1/service/monitoring/bin/check.sh').exists()

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produces manifest-classpath jar and windows start script with no classpath length limitations'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        settingsFile << '''
        rootProject.name = 'root-project'
        '''

        buildFile << '''
            distribution {
                enableManifestClasspath true
            }
            dependencies {
              implementation "com.google.guava:guava:19.0"
            }
        '''.stripIndent(true)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        String startScript = new File(projectDir, 'dist/service-name-0.0.1/service/bin/service-name.bat').text
        startScript.contains("-manifest-classpath-0.0.1.jar")
        !startScript.contains("-classpath \"%CLASSPATH%\"")
        def classpathJar = new File(projectDir, 'dist/service-name-0.0.1/service/lib/').listFiles()
                .find({ it.name.endsWith("-manifest-classpath-0.0.1.jar") })
        classpathJar.exists()

        def zipManifest = readFromZip(classpathJar, "META-INF/MANIFEST.MF").replace('\r\n ', '')
        zipManifest.contains('Class-Path: ')
        zipManifest.contains('guava-19.0.jar')
        zipManifest.contains('root-project-manifest-classpath-0.0.1.jar')
        zipManifest.contains('root-project-0.0.1.jar')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: does not produce manifest-classpath jar when disabled in extension'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        String startScript = new File(projectDir, 'dist/service-name-0.0.1/service/bin/service-name.bat').text
        !startScript.contains("-manifest-classpath-0.1.jar")
        startScript.contains("-classpath \"%CLASSPATH%\"")
        !new File(projectDir, 'dist/service-name-0.0.1/service/lib/').listFiles()
                .find({ it.name.endsWith("-manifest-classpath-0.1.jar") })

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: distTar artifact name is set during appropriate lifecycle events'() {
        given:
        gradleVersion = gradleVersionNumber
        buildFile << '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                mavenCentral()
            }
            distribution {
                serviceName "my-service"
                mainClass "dummy.service.MainClass"
                args "hello"
            }

            afterEvaluate {
                String actualTarballPath = distTar.outputs.files.singleFile.absolutePath
                String expectedTarballPath = project.file('build/distributions/my-service.sls.tgz').absolutePath

                if (!actualTarballPath.equals(expectedTarballPath)) {
                    throw new GradleException("tarball path didn't match.\\n" +
                            "actual: ${actualTarballPath}\\n" +
                            "expected: ${expectedTarballPath}")
                }
            }
        '''.stripIndent(true)

        expect:
        runTasks(':tasks')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: exposes an artifact through the sls configuration'() {
        given:
        gradleVersion = gradleVersionNumber
        helper.addSubproject('parent', '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                mavenCentral()
            }
            version '0.0.1'
            distribution {
                serviceName "my-service"
                mainClass "dummy.service.MainClass"
                args "hello"
            }
        ''')

        def childProject = helper.addSubproject('child', '''
            configurations {
                fromOtherProject
            }
            dependencies {
                fromOtherProject project(path: ':parent', configuration: 'sls')
            }
            task untar(type: Copy) {
                // ensures the artifact is built by depending on the configuration
                dependsOn configurations.fromOtherProject

                // copy the contents of the tarball
                from { tarTree(configurations.fromOtherProject.singleFile) }
                into 'build/exploded'
            }
        ''')

        when:
        def buildResult = runTasks(':child:untar')

        then:
        buildResult.task(':parent:distTar').outcome == TaskOutcome.SUCCESS
        new File(childProject, 'build/exploded/my-service-0.0.1/deployment/manifest.yml').exists()

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: exposes an artifact via dependency with sls-dist usage'() {
        given:
        gradleVersion = gradleVersionNumber
        helper.addSubproject('producer', '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                mavenCentral()
            }
            version '0.0.1'
            distribution {
                serviceName "my-service"
                mainClass "dummy.service.MainClass"
                args "hello"
            }
        ''')

        def consumer = helper.addSubproject('consumer', '''
            configurations {
                fromOtherProject {
                    attributes {
                        attribute Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'sls-dist')
                    }
                }
            }
            dependencies {
                fromOtherProject project(':producer')
            }
            task untar(type: Copy) {
                // ensures the artifact is built by depending on the configuration
                dependsOn configurations.fromOtherProject

                // copy the contents of the tarball
                from { tarTree(configurations.fromOtherProject.singleFile) }
                into 'build/exploded'
            }
        ''')

        when:
        def buildResult = runTasks(':consumer:untar')

        then:
        buildResult.task(':producer:distTar').outcome == TaskOutcome.SUCCESS
        new File(consumer, 'build/exploded/my-service-0.0.1/deployment/manifest.yml').exists()

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    /**
     * Note: in this test, we are not checking that we can resolve exactly the right artifact,
     * as that is tricky to get right, when the configuration being resolved doesn't set any required attributes.
     *
     * For instance, if java happens to be applied to the project, gradle will ALWAYS prefer the
     * runtimeElements variant (from configuration runtimeElements) so our {@code sls} variant won't be selected.
     * However, here we only care about testing that it can resolve to <i>something</i>, for the sole purpose of
     * extracting the version the resolved component.
     */
    def '#gradleVersionNumber: dist project can be resolved through plain dependency when GCV is applied'() {
        setup:
        gradleVersion = gradleVersionNumber
        buildFile << """
            plugins {
                id 'com.palantir.consistent-versions' version '${Versions.GRADLE_CONSISTENT_VERSIONS}'
            }

            configurations {
                fromOtherProject
            }
            dependencies {
                fromOtherProject project(':dist')
            }

            task verify {
                doLast {
                    configurations.fromOtherProject.resolve()
                }
            }
        """.stripIndent(true)

        helper.addSubproject('dist', '''
            plugins {
                id 'com.palantir.sls-java-service-distribution'
            }

            version '0.0.1'
            distribution {
                serviceName "my-asset"
                mainClass "dummy.service.MainClass"
                args "hello"
            }
        ''')

        runTasks('--write-locks')

        expect:
        runTasks(':verify')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: fails when asset and service plugins are both applied'() {
        given:
        gradleVersion = gradleVersionNumber
        buildFile << '''
            plugins {
                id 'com.palantir.sls-asset-distribution'
                id 'com.palantir.sls-java-service-distribution'
            }
        '''.stripIndent(true)

        when:
        def result = runTasksAndFail(":tasks")

        then:
        result.output.contains("The plugins 'com.palantir.sls-asset-distribution' and 'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.")

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: uses the runtimeClasspath so api and implementation configurations work with java-library plugin'() {
        given:
        gradleVersion = gradleVersionNumber
        def parent = helper.addSubproject('parent', '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            version '0.0.1'
            distribution {
                serviceName "service-name"
                mainClass "dummy.service.MainClass"
                args "hello"
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation project(':child')
                implementation 'org.mockito:mockito-core:2.7.22'
            }
        ''')

        createUntarTask(new File(parent, "build.gradle"))

        helper.addSubproject('child', '''
            plugins {
                id 'java-library'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                api "com.google.guava:guava:19.0"
                implementation "com.google.code.findbugs:annotations:3.0.1"
            }
        ''')

        when:
        runTasks(':parent:build', ':parent:distTar', ':parent:untar')

        then:
        def libFiles = new File(projectDir, 'parent/dist/service-name-0.0.1/service/lib/').listFiles()
        libFiles.any { it.toString().endsWith('annotations-3.0.1.jar') }
        libFiles.any { it.toString().endsWith('guava-19.0.jar') }
        libFiles.any { it.toString().endsWith('mockito-core-2.7.22.jar') }
        !libFiles.any { it.toString().equals('main') }

        // verify start scripts
        List<String> startScript = new File(projectDir, 'parent/dist/service-name-0.0.1/service/bin/service-name')
                .text
                .find(/CLASSPATH=(.*)/) { match, classpath -> classpath }
                .split(':')

        startScript.any { it.contains('/lib/annotations-3.0.1.jar') }
        startScript.any { it.contains('/lib/guava-19.0.jar') }
        startScript.any { it.contains('/lib/mockito-core-2.7.22.jar') }

        // verify launcher YAML files
        LaunchConfig.LaunchConfigInfo launcherCheck = OBJECT_MAPPER.readValue(
                new File(projectDir, 'parent/dist/service-name-0.0.1/service/bin/launcher-check.yml'),
                LaunchConfig.LaunchConfigInfo.class)

        launcherCheck.classpath.any { it.contains('/lib/annotations-3.0.1.jar') }
        launcherCheck.classpath.any { it.contains('/lib/guava-19.0.jar') }
        launcherCheck.classpath.any { it.contains('/lib/mockito-core-2.7.22.jar') }

        LaunchConfig.LaunchConfigInfo launcherStatic = OBJECT_MAPPER.readValue(
                new File(projectDir, 'parent/dist/service-name-0.0.1/service/bin/launcher-static.yml'),
                LaunchConfig.LaunchConfigInfo.class)

        launcherStatic.classpath.any { it.contains('/lib/annotations-3.0.1.jar') }
        launcherStatic.classpath.any { it.contains('/lib/guava-19.0.jar') }
        launcherStatic.classpath.any { it.contains('/lib/mockito-core-2.7.22.jar') }

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    @Unroll
    def '#gradleVersionNumber: docker can resolve inter-project product dependencies (#writeLocksTask)'() {
        setup:
        gradleVersion = gradleVersionNumber
        buildFile << """
            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath 'com.palantir.gradle.docker:gradle-docker:0.36.0'
                }
            }
            apply plugin: 'com.palantir.docker-compose'
            allprojects {
                group = 'group'
                version = '1.0.0'
            }

        """.stripIndent(true)
        helper.addSubproject("first", """
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }
            distribution {
                mainClass "dummy.service.MainClass"
                productDependency {
                    productGroup = 'group'
                    productName = 'second-product'
                    minimumVersion = project.version
                }
            }
        """.stripIndent(true))
        helper.addSubproject("second", """
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }
            distribution {
                serviceName = 'second-product'
                mainClass "dummy.service.MainClass"
            }
        """.stripIndent(true))

        runTasks(writeLocksTask)

        // We're just using generateDockerCompose as it conveniently resolves the 'docker' configuration for us
        // Which in turn, conveniently depends on all subprojects' `productDependencies` configurations
        file("docker-compose.yml.template").text = ''

        expect:
        runTasks("generateDockerCompose")

        where:
        [writeLocksTask, gradleVersionNumber] << [
                ['--write-locks', 'writeProductDependenciesLocks'],
                GradleTestVersions.GRADLE_VERSIONS
        ].combinations()
    }

    def '#gradleVersionNumber: uses the runtimeClasspath in manifest jar'() {
        given:
        gradleVersion = gradleVersionNumber
        def parent = helper.addSubproject('parent', '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            version '0.0.1'
            distribution {
                serviceName "service-name"
                mainClass "dummy.service.MainClass"
                args "hello"
                enableManifestClasspath true
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                implementation project(':child')
                implementation 'org.mockito:mockito-core:2.7.22'
            }
        ''')

        createUntarTask(new File(parent, "build.gradle"))

        helper.addSubproject('child', '''
            plugins {
                id 'java-library'
            }
            repositories {
                mavenCentral()
            }
            dependencies {
                api "com.google.guava:guava:19.0"
                implementation "com.google.code.findbugs:annotations:3.0.1"
            }
        ''')

        when:
        runTasks(':parent:build', ':parent:distTar', ':parent:untar')

        then:
        def libFiles = new File(projectDir, 'parent/dist/service-name-0.0.1/service/lib/').listFiles()
        libFiles.any { it.toString().endsWith('annotations-3.0.1.jar') }
        libFiles.any { it.toString().endsWith('guava-19.0.jar') }
        libFiles.any { it.toString().endsWith('mockito-core-2.7.22.jar') }
        !libFiles.any { it.toString().equals('main') }

        def classpathJar = libFiles.find { it.name.endsWith("-manifest-classpath-0.0.1.jar") }
        classpathJar.exists()

        // verify META-INF/MANIFEST.MF
        String manifestContents = readFromZip(classpathJar, "META-INF/MANIFEST.MF")
                .readLines()
                .collect { it.trim() }
                .join('')

        manifestContents.contains('annotations-3.0.1.jar')
        manifestContents.contains('guava-19.0.jar')
        manifestContents.contains('mockito-core-2.7.22.jar')
        !manifestContents.contains('main')

        // verify start scripts
        List<String> startScript = new File(projectDir, 'parent/dist/service-name-0.0.1/service/bin/service-name')
                .text
                .find(/CLASSPATH=(.*)/) { match, classpath -> classpath }
                .split(':')

        startScript.any { it.contains('-manifest-classpath-0.0.1.jar') }

        // verify launcher YAML files
        LaunchConfig.LaunchConfigInfo launcherCheck = OBJECT_MAPPER.readValue(
                new File(projectDir, 'parent/dist/service-name-0.0.1/service/bin/launcher-check.yml'),
                LaunchConfig.LaunchConfigInfo.class)

        launcherCheck.classpath.any { it.contains('-manifest-classpath-0.0.1.jar') }

        LaunchConfig.LaunchConfigInfo launcherStatic = OBJECT_MAPPER.readValue(
                new File(projectDir, 'parent/dist/service-name-0.0.1/service/bin/launcher-static.yml'),
                LaunchConfig.LaunchConfigInfo.class)

        launcherStatic.classpath.any { it.contains('-manifest-classpath-0.0.1.jar') }

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: project class files do not appear in output lib directory'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        file('src/main/java/test/Test.java') << '''
        package test;
        public class Test {
            public static void main(String[] args) {
                while(true);
            }
        }
        '''.stripIndent(true)

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        !new File(projectDir, 'dist/service-name-0.0.1/service/lib/com/test/Test.class').exists()

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: adds initiatingOccupancyFraction gc profile jvm settings'() {
        given:
        gradleVersion = gradleVersionNumber
        buildFile << '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                mavenCentral()
            }

            version '0.0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                // only available on JDKs < 14
                javaVersion 11
                gc 'response-time', {
                    initiatingOccupancyFraction 75
                }
            }
        '''.stripIndent(true)

        createUntarTask(buildFile)

        when:
        runTasks(':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        actualStaticConfig.jvmOpts.containsAll(['-XX:+UseParNewGC', '-XX:+UseConcMarkSweepGC', '-XX:CMSInitiatingOccupancyFraction=75'])

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: adds maxGCPauseMillis gc profile jvm settings'() {
        given:
        gradleVersion = gradleVersionNumber
        buildFile << '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                mavenCentral()
            }

            version '0.0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                gc 'hybrid', {
                    maxGCPauseMillis 1234
                }
            }
        '''.stripIndent(true)

        createUntarTask(buildFile)

        when:
        runTasks(':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        actualStaticConfig.jvmOpts.containsAll(['-XX:+UseG1GC', '-XX:+UseNUMA', '-XX:MaxGCPauseMillis=1234'])

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: gc profile null configuration closure'() {
        given:
        gradleVersion = gradleVersionNumber
        buildFile << '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            repositories {
                mavenCentral()
            }

            version '0.0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                gc 'hybrid'
            }
        '''.stripIndent(true)

        createUntarTask(buildFile)

        when:
        runTasks(':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        actualStaticConfig.jvmOpts.containsAll(['-XX:+UseG1GC', '-XX:+UseNUMA', "-XX:MaxGCPauseMillis=500"])

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: applies java agents'() {
        setup:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies {
                implementation files("${EXTERNAL_JAR}")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        actualStaticConfig.jvmOpts().contains("-javaagent:service/lib/agent/byte-buddy-agent-1.10.21.jar")
        fileExists('dist/service-name-0.0.1/service/lib/agent/byte-buddy-agent-1.10.21.jar')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: fails at build time when non-agent jars are provided as agents'() {
        setup:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies {
                implementation files("${EXTERNAL_JAR}")
                javaAgent files("${EXTERNAL_JAR}")
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        def result = runTasksAndFail(':distTar')

        then:
        result.output.contains('is not a java agent and contains no Premain-Class manifest entry')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: exports management packages on new javas'() {
        setup:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies { implementation files("${EXTERNAL_JAR}") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        actualStaticConfig.jvmOpts().containsAll([
                "--add-exports",
                "java.management/sun.management=ALL-UNNAMED"
        ])

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: applies exports based on classpath manifests'() {
        setup:
        gradleVersion = gradleVersionNumber
        Manifest manifest = new Manifest()
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
        manifest.getMainAttributes().putValue('Add-Exports', 'jdk.compiler/com.sun.tools.javac.file')
        File testJar = new File(getProjectDir(), "test.jar");
        testJar.withOutputStream { fos ->
            new JarOutputStream(fos, manifest).close()
        }
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies {
                implementation files("test.jar")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def actualOpts = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'),
                LaunchConfig.LaunchConfigInfo)
                .jvmOpts()

        // Quick check
        actualOpts.containsAll([
                "--add-exports",
                "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"])

        // Verify args are set in the correct order
        int compilerPairIndex = actualOpts.indexOf("jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED")
        actualOpts.get(compilerPairIndex - 1) == "--add-exports"

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: applies opens based on classpath manifests'() {
        setup:
        gradleVersion = gradleVersionNumber
        Manifest manifest = new Manifest()
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
        manifest.getMainAttributes().putValue('Add-Opens', 'jdk.compiler/com.sun.tools.javac.file')
        File testJar = new File(getProjectDir(), "test.jar");
        testJar.withOutputStream { fos ->
            new JarOutputStream(fos, manifest).close()
        }
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies {
                implementation files("test.jar")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def actualOpts = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'),
                LaunchConfig.LaunchConfigInfo)
                .jvmOpts()

        // Quick check
        actualOpts.containsAll([
                "--add-opens",
                "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"])

        // Verify args are set in the correct order
        int compilerPairIndex = actualOpts.indexOf("jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED")
        actualOpts.get(compilerPairIndex - 1) == "--add-opens"

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: applies opens based on classpath manifests for manifest classpaths'() {
        setup:
        gradleVersion = gradleVersionNumber
        Manifest manifest = new Manifest()
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0")
        manifest.getMainAttributes().putValue('Add-Opens', 'jdk.compiler/com.sun.tools.javac.file')
        File testJar = new File(getProjectDir(), "test.jar");
        testJar.withOutputStream { fos ->
            new JarOutputStream(fos, manifest).close()
        }
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies {
                implementation files("test.jar")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
                enableManifestClasspath true
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def actualOpts = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'),
                LaunchConfig.LaunchConfigInfo)
                .jvmOpts()

        // Quick check
        actualOpts.containsAll([
                "--add-opens",
                "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"])

        // Verify args are set in the correct order
        int compilerPairIndex = actualOpts.indexOf("jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED")
        actualOpts.get(compilerPairIndex - 1) == "--add-opens"

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: Handles jars with no manifest'() {
        setup:
        gradleVersion = gradleVersionNumber
        File testJar = new File(getProjectDir(), "test.jar");
        testJar.withOutputStream { fos ->
            new ZipOutputStream(fos).close()
        }
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies {
                implementation files("test.jar")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        BuildResult result = runTasks(':build', ':distTar', ':untar')

        then:
        tasksWereSuccessful(result, ':build', ':distTar', ':untar')

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: can resolve go-java-launcher binaries through GCV'() {
        setup:
        gradleVersion = gradleVersionNumber
        // Set a lower default version of go-java-launcher so we can verify that we pick up the higher version through
        // GCV
        file('gradle.properties') << """
        ${JavaServiceDistributionPlugin.TEST_GO_JAVA_LAUNCHER_FALLBACK_VERSION_OVERRIDE}=1.17.0
        """.stripIndent(true)

        def goJavaLauncherVersion = "1.18.0"

        buildFile.text = """
        plugins {
            id 'com.palantir.consistent-versions' version '${Versions.GRADLE_CONSISTENT_VERSIONS}'
            id 'com.palantir.sls-java-service-distribution'
        }

        repositories {
            mavenCentral()
        }

        version '0.0.1'
        distribution {
            serviceName 'service-name'
            mainClass 'test.Test'
        }
        """.stripIndent(true)

        file('versions.props') << """
        com.palantir.launching:* = ${goJavaLauncherVersion}
        """.stripIndent(true)

        createUntarTask(buildFile)

        when:
        def res = runTasks(':distTar', ':untar', '--write-locks')

        then:
        // Verify the test version is actually used
        res.output.contains("using test only version override for go-java-launcher: 1.17.0")

        fileExists("dist/service-name-0.0.1/service/bin/go-java-launcher-${goJavaLauncherVersion}/service/bin")
        fileExists("dist/service-name-0.0.1/service/bin/go-init-${goJavaLauncherVersion}/service/bin")

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: enableAlwaysPreTouch'() {
        setup:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)
        buildFile << """
            dependencies { implementation files("${EXTERNAL_JAR}") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                enableAlwaysPreTouch()
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        def actualStaticConfig = OBJECT_MAPPER.readValue(
                new File(projectDir, 'dist/service-name-0.0.1/service/bin/launcher-static.yml'), LaunchConfig.LaunchConfigInfo)
        actualStaticConfig.jvmOpts().containsAll([
                "-XX:+AlwaysPreTouch",
                "-XX:+UseTransparentHugePages",
        ])

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: produce distribution bundle that can bundle extra Jars'() {
        given:
        gradleVersion = gradleVersionNumber
        createUntarBuildFile(buildFile)

        buildFile << """
            dependencies { implementation files("${EXTERNAL_JAR}") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
                javaHome 'foo'
                extraFiles {
                    into('service/maven') {
                      from(files("${EXTERNAL_JAR}"))
                    }
                }
            }""".stripIndent(true)
        file('src/main/java/test/Test.java') << """
          package test;
          public class Test {}
        """

        when:
        runTasks(':build', ':distTar', ':untar')

        then:
        fileExists("dist/service-name-0.0.1/service/maven/${Path.of(EXTERNAL_JAR).getFileName()}")

        where:
        gradleVersionNumber << GradleTestVersions.GRADLE_VERSIONS
    }

    private static createUntarBuildFile(File buildFile) {
        buildFile << '''
            plugins {
                id 'java'
                id 'com.palantir.sls-java-service-distribution'
            }

            project.group = 'service-group'

            repositories {
                mavenCentral()
            }

            version '0.0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                defaultJvmOpts '-Xmx4M', '-Djavax.net.ssl.trustStore=truststore.jks'
                manifestExtensions 'foo': [
                    'bar': ['1', '2']
                ]
            }

            java {
                sourceCompatibility = '1.7'
            }
        '''.stripIndent(true)

        createUntarTask(buildFile)
    }

    static void createUntarTask(File file) {
        file << """
            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from { tarTree(tasks.distTar.outputs.files.singleFile) }
                into "dist"
                dependsOn distTar
                duplicatesStrategy = 'INCLUDE'
            }
        """.stripIndent(true)
    }

    def readFromZip(File zipFile, String pathInZipFile) {
        def zf = new ZipFile(zipFile)
        def object = zf.entries().find { it.name == pathInZipFile }
        return zf.getInputStream(object).text
    }

    int execWithExitCode(String... tasks) {
        ProcessBuilder pb = new ProcessBuilder().command(tasks).directory(projectDir).inheritIO()
        pb.environment().put("JAVA_HOME", System.getProperty("java.home"))
        Process proc = pb.start()
        int result = proc.waitFor()
        return result
    }

    String execWithOutput(String... tasks) {
        StringBuffer sout = new StringBuffer(), serr = new StringBuffer()
        ProcessBuilder pb = new ProcessBuilder().command(tasks).directory(projectDir);
        pb.environment().put("JAVA_HOME", System.getProperty("java.home"))
        Process proc = pb.start()
        proc.consumeProcessOutput(sout, serr)
        int result = proc.waitFor()
        int expected = 0
        Assert.assertEquals(sprintf("Expected command '%s' to exit with '%d'\nstdout: %s\nstderr: %s",
                tasks.join(' '), expected, sout, serr), expected, result)
        return sout.toString()
    }

    void execAllowFail(String... tasks) {
        ProcessBuilder pb = new ProcessBuilder().command(tasks).directory(projectDir)
                .inheritIO()
        pb.environment().put("JAVA_HOME", System.getProperty("java.home"))
        pb.start().waitFor()
    }
}
