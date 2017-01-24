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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.palantir.gradle.dist.GradleTestSpec
import com.palantir.gradle.dist.service.tasks.LaunchConfigTask
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure

import java.nio.file.Files

class JavaDistributionPluginTests extends GradleTestSpec {

    def 'produce distribution bundle and check start, stop, restart, check behavior'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                checkArgs 'healthcheck'
            }
        '''.stripIndent()
        file('var/conf/launcher-custom.yml') << '''
            configType: java
            configVersion: 1
            jvmOpts:
              - '-Dcustom.property=myCustomValue'
        '''.stripIndent()

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
        '''.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        // try all of the service commands
        exec('dist/service-name-0.1/service/bin/init.sh', 'start') ==~ /(?m)Running 'service-name'\.\.\.\s+Started \(\d+\)\n/
        file('dist/service-name-0.1/var/log/service-name-startup.log', projectDir).text.contains('Test started\n')
        exec('dist/service-name-0.1/service/bin/init.sh', 'start') ==~ /(?m)Process is already running\n/
        exec('dist/service-name-0.1/service/bin/init.sh', 'status') ==~ /(?m)Checking 'service-name'\.\.\.\s+Running \(\d+\)\n/
        exec('dist/service-name-0.1/service/bin/init.sh', 'restart') ==~
                /(?m)Stopping 'service-name'\.\.\.\s+Stopped \(\d+\)\nRunning 'service-name'\.\.\.\s+Started \(\d+\)\n/
        exec('dist/service-name-0.1/service/bin/init.sh', 'stop') ==~ /(?m)Stopping 'service-name'\.\.\.\s+Stopped \(\d+\)\n/
        exec('dist/service-name-0.1/service/bin/init.sh', 'check') ==~ /(?m)Checking health of 'service-name'\.\.\.\s+Healthy.*\n/
        exec('dist/service-name-0.1/service/monitoring/bin/check.sh') ==~ /(?m)Checking health of 'service-name'\.\.\.\s+Healthy.*\n/
    }

    def 'packaging tasks re-run after version change'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            distribution {
                enableManifestClasspath true
            }
            task untar02 (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/service-name-0.2.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
         '''.stripIndent()
        file('src/main/java/test/Test.java') << '''
        package test;
        public class Test {
            public static void main(String[] args) {
                while(true);
            }
        }
        '''.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')
        buildFile << '''
            version '0.2'
        '''.stripIndent()

        then:
        def version02BuildOutput = runSuccessfully(':build', ':distTar', ':untar02').output
        version02BuildOutput ==~ /(?m)(?s).*^:createCheckScript UP-TO-DATE$.*/
        version02BuildOutput ==~ /(?m)(?s).*^:createInitScript UP-TO-DATE$.*/
        version02BuildOutput ==~ /(?m)(?s).*^:createLaunchConfig$.*/
        version02BuildOutput ==~ /(?m)(?s).*^:createManifest$.*/
        version02BuildOutput ==~ /(?m)(?s).*^:manifestClasspathJar$.*/
        version02BuildOutput ==~ /(?m)(?s).*^:distTar$.*/
        exec('dist/service-name-0.2/service/bin/init.sh', 'start') ==~ /(?m)Running 'service-name'\.\.\.\s+Started \(\d+\)\n/
        exec('dist/service-name-0.2/service/bin/init.sh', 'stop') ==~ /(?m)Stopping 'service-name'\.\.\.\s+Stopped \(\d+\)\n/
    }

    def 'status reports when process name and id don"t match'() {
        given:
        createUntarBuildFile(buildFile)
        file('src/main/java/test/Test.java') << '''
        package test;
        public class Test {
            public static void main(String[] args) {
                while(true);
            }
        }
        '''.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')
        Files.move(file('dist/service-name-0.1').toPath(), file('dist/foo').toPath())

        then:
        exec('dist/foo/service/bin/init.sh', 'start') ==~ /(?m)Running 'service-name'\.\.\.\s+Started \(\d+\)\n/
        exec('dist/foo/service/bin/init.sh', 'status').contains('appears to not correspond to service service-name')
        exec('dist/foo/service/bin/init.sh', 'stop') ==~ /(?m)Stopping 'service-name'\.\.\.\s+Stopped \(\d+\)\n/
    }

    def 'produce distribution bundle and check var/log and var/run are excluded'() {
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

    def 'produce distribution bundle and check var/data/tmp is created and used for temporary files'() {
        given:
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
        '''.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        execAllowFail('dist/service-name-0.1/service/bin/init.sh', 'start')
        file('dist/service-name-0.1/var/data/tmp').listFiles().length == 1
        file('dist/service-name-0.1/var/data/tmp').listFiles()[0].text == "temp content"
    }

    def 'produce distribution bundle with custom exclude set'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'java'
            }
            repositories { jcenter() }

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
                from tarTree(resources.gzip("${buildDir}/distributions/service-name-0.1.sls.tgz"))
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
        !file('dist/service-name-0.1/var/data/database').exists()
        file('dist/service-name-0.1/var/conf/service-name.yml').exists()
    }

    def 'produce distribution bundle with a non-string version object'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'java'
            }
            repositories { jcenter() }

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
                from tarTree(resources.gzip("${buildDir}/distributions/service-name-0.1.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        String manifest = file('dist/service-name-0.1/deployment/manifest.yml', projectDir).text
        manifest.contains('"product-version": "0.1"')
    }

    def 'manifest file contains expected fields'() {
        given:
        createUntarBuildFile(buildFile)

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        String manifest = file('dist/service-name-0.1/deployment/manifest.yml', projectDir).text
        manifest.contains('"manifest-version": "1.0"')
        manifest.contains('"product-group": "service-group"')
        manifest.contains('"product-name": "service-name"')
        manifest.contains('"product-version": "0.1"')
        manifest.contains('"product-type": "service.v1"')
        manifest.replaceAll(/\s/, '').contains('"extensions":{"foo":{"bar":["1","2"]}')
    }

    def 'can specify service dependencies'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << """
            distribution {
                serviceDependency "group1", "name1", "1.0.0", "2.0.0"
                serviceDependency "group2", "name2", "1.0.0", "2.0.0", "1.5.0"
            }
        """.stripIndent()

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        def mapper = new ObjectMapper()
        def manifest= mapper.readValue(file('dist/service-name-0.1/deployment/manifest.yml', projectDir), Map)

        def dep1 = manifest['extensions']['service-dependencies'][0]
        dep1['group'] == 'group1'
        dep1['name'] == 'name1'
        dep1['minVersion'] == '1.0.0'
        dep1['maxVersion'] == '2.0.0'
        dep1['recommendedVersion'] == null

        def dep2 = manifest['extensions']['service-dependencies'][1]
        dep2['group'] == 'group2'
        dep2['name'] == 'name2'
        dep2['minVersion'] == '1.0.0'
        dep2['maxVersion'] == '2.0.0'
        dep2['recommendedVersion'] == "1.5.0"
    }

    def 'cannot specify service dependencies with invalid versions'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << """
            distribution {
                serviceDependency "group1", "name1", "1.0.0foo", "2.0.0"
            }
        """.stripIndent()

        when:
        run(':distTar').build()

        then:
        UnexpectedBuildFailure exception = thrown()
        exception.message.contains("Invalid SLS version: 1.0.0foo")
    }

    def 'produce distribution bundle with files in deployment/'() {
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
        manifest.contains('"product-name": "service-name"')

        // check files in deployment/ copied successfully
        String actualConfiguration = file('dist/service-name-0.1/deployment/configuration.yml', projectDir).text
        actualConfiguration.equals(deploymentConfiguration)
    }

    def 'produce distribution bundle with start script that passes default JVM options'() {
        given:
        createUntarBuildFile(buildFile)

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        String startScript = file('dist/service-name-0.1/service/bin/service-name', projectDir).text
        startScript.contains('DEFAULT_JVM_OPTS=\'"-Xmx4M" "-Djavax.net.ssl.trustStore=truststore.jks"\'')
    }

    def 'produce distribution bundle that populates launcher-static.yml and launcher-check.yml'() {
        given:
        createUntarBuildFile(buildFile)
        buildFile << '''
            dependencies { compile files("external.jar") }
            tasks.jar.baseName = "internal"
            distribution {
                javaHome 'foo'
                args 'myArg1', 'myArg2'
                checkArgs 'myCheckArg1', 'myCheckArg2'
                env "key1": "val1",
                    "key2": "val2"
            }'''.stripIndent()
        file('src/main/java/test/Test.java') << "package test;\npublic class Test {}"

        when:
        runSuccessfully(':build', ':distTar', ':untar')

        then:
        def expectedStaticConfig = new LaunchConfigTask.StaticLaunchConfig()
        expectedStaticConfig.setConfigVersion(1)
        expectedStaticConfig.setConfigType("java")
        expectedStaticConfig.setMainClass("test.Test")
        expectedStaticConfig.setJavaHome("foo")
        expectedStaticConfig.setArgs(['myArg1', 'myArg2'])
        expectedStaticConfig.setClasspath(['service/lib/internal-0.1.jar', 'service/lib/external.jar'])
        expectedStaticConfig.setJvmOpts([
                '-Djava.io.tmpdir=var/data/tmp',
                '-XX:+PrintGCDateStamps',
                '-XX:+PrintGCDetails',
                '-XX:-TraceClassUnloading',
                '-XX:+UseGCLogFileRotation',
                '-XX:GCLogFileSize=10M',
                '-XX:NumberOfGCLogFiles=10',
                '-Xloggc:var/log/gc-%t-%p.log',
                '-verbose:gc',
                '-Xmx4M',
                '-Djavax.net.ssl.trustStore=truststore.jks'])
        expectedStaticConfig.setEnv([
                "key1": "val1",
                "key2": "val2"
        ])
        def actualStaticConfig = new ObjectMapper(new YAMLFactory()).readValue(
                file('dist/service-name-0.1/service/bin/launcher-static.yml'), LaunchConfigTask.StaticLaunchConfig)
        expectedStaticConfig == actualStaticConfig

        def expectedCheckConfig = expectedStaticConfig
        expectedCheckConfig.setJvmOpts([
                '-Djava.io.tmpdir=var/data/tmp',
                '-Xmx4M',
                '-Djavax.net.ssl.trustStore=truststore.jks'])
        expectedCheckConfig.setArgs(['myCheckArg1', 'myCheckArg2'])
        def actualCheckConfig = new ObjectMapper(new YAMLFactory()).readValue(
                file('dist/service-name-0.1/service/bin/launcher-check.yml'), LaunchConfigTask.StaticLaunchConfig)
        expectedCheckConfig == actualCheckConfig
    }

    def 'produce distribution bundle that populates check.sh'() {
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

    def 'produces manifest-classpath jar and windows start script with no classpath length limitations'() {
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
                .find({ it.name.endsWith("-manifest-classpath-0.1.jar") })
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
                .find({ it.name.endsWith("-manifest-classpath-0.1.jar") })
    }

    def 'distTar artifact name is set during appropriate lifecycle events'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'java'
            }
            repositories { jcenter() }
            distribution {
                serviceName "my-service"
                mainClass "dummy.service.MainClass"
                args "hello"
            }

            afterEvaluate {
                println "distTar: ${distTar.outputs.files.singleFile}"
            }
        '''.stripIndent()

        when:
        BuildResult buildResult = runSuccessfully(':tasks')

        then:
        buildResult.output =~ ("distTar: ${projectDir}/build/distributions/my-service.sls.tgz")
    }

    def 'exposes an artifact through the sls configuration'() {
        given:
        helper.addSubproject('parent', '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'java'
            }
            repositories { jcenter() }
            version '0.1'
            distribution {
                serviceName "my-service"
                mainClass "dummy.service.MainClass"
                args "hello"
            }
        ''')

        helper.addSubproject('child', '''
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
                from tarTree(configurations.fromOtherProject.singleFile)
                into 'build/exploded'
            }
        ''')

        when:
        BuildResult buildResult = runSuccessfully(':child:untar')

        then:
        buildResult.task(':parent:distTar').outcome == TaskOutcome.SUCCESS
        file('child/build/exploded/my-service-0.1/deployment/manifest.yml')
    }

    def 'fails when asset and service plugins are both applied'() {
        given:
        buildFile << '''
            plugins {
                id 'com.palantir.asset-distribution'
                id 'com.palantir.java-distribution'
            }
        '''.stripIndent()

        when:
        def result = run(":tasks").buildAndFail()

        then:
        result.output.contains("The plugins 'com.palantir.asset-distribution' and 'com.palantir.java-distribution' cannot be used in the same Gradle project.")
    }

    private static createUntarBuildFile(buildFile) {
        buildFile << '''
            plugins {
                id 'com.palantir.java-distribution'
                id 'java'
            }

            project.group = 'service-group'

            repositories { jcenter() }

            version '0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                defaultJvmOpts '-Xmx4M', '-Djavax.net.ssl.trustStore=truststore.jks'
                manifestExtensions 'foo': [
                    'bar': ['1', '2']
                ]
            }

            sourceCompatibility = '1.7'

            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from tarTree(resources.gzip("${buildDir}/distributions/service-name-0.1.sls.tgz"))
                into "${projectDir}/dist"
                dependsOn distTar
            }
        '''.stripIndent()
    }
}
