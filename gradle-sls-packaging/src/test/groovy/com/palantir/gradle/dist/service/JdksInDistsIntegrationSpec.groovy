/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.gradle.dist.service

import nebula.test.IntegrationSpec
import org.rauschig.jarchivelib.ArchiveFormat
import org.rauschig.jarchivelib.ArchiverFactory
import org.rauschig.jarchivelib.CompressionType


class JdksInDistsIntegrationSpec extends IntegrationSpec {
    def setup() {
        // language=gradle
        settingsFile << '''
            rootProject.name = 'myService'
        '''.stripIndent(true)

        // language=gradle
        buildFile << '''
            apply plugin: 'java'
            apply plugin: 'com.palantir.sls-java-service-distribution'

            repositories {
                maven {
                    url 'https://artifactory.palantir.build/artifactory/release-jar'
                    metadataSources { mavenPom(); ignoreGradleMetadataRedirection() }
                }
                mavenLocal()
            }
            
            group 'group'
            version '1.0.0'
        '''.stripIndent(true)

        file('versions.lock')

        // language=java
        writeJavaSourceFile '''
            package app;
            
            import java.nio.file.Files;
            import java.nio.file.Paths;
            
            public class Main {
                public static void main(String... args) {}
            }
        '''.stripIndent(true)

        file('build/fake-jdk/release') << 'its a jdk trust me'
        def java = file('build/fake-jdk/bin/java') << '''
            #!/bin/bash
            echo "Hello"
            echo "JAVA_17_HOME=$JAVA_17_HOME"
        '''.stripIndent(true)

        java.setExecutable(true)
    }

    def 'puts jdk in dist'() {
        // language=gradle
        buildFile << '''
            distribution {
                javaVersion JavaVersion.VERSION_17
                jdks.put(JavaVersion.VERSION_17, fileTree('build/fake-jdk'))
            }
        '''.stripIndent(true)

        when:
        runTasksSuccessfully('distTar')

        then:
        def rootDir = extractDist()
        def jdkDir = new File(rootDir, "service/jdk17")
        jdkDir.exists()

        def releaseFileText = new File(jdkDir, "release").text

        releaseFileText.contains 'its a jdk trust me'

        def launcherStatic = new File(rootDir, "service/bin/launcher-static.yml").text
        launcherStatic.contains 'javaHome: "service/myService-1.0.0-jdks/jdk17"'

        // Only contains a linux amd64 JDK, will only run on CI
        if ("true" == System.getenv("CI")) {
            "${rootDir}/service/bin/init.sh start".execute([], rootDir).waitFor() == 0

            def startupLog = new File(rootDir, "var/logs/output.log")
            startupLog.text.contains "Hello"
            startupLog.text.contains "JAVA_17_HOME=service/myService-1.0.0-jdks/jdk17"
        }
    }

    private File extractDist() {
        def slsTgz = new File(projectDir, "build/distributions/myService-1.0.0.sls.tgz")
        def extracted = new File(slsTgz.getParent(), "extracted")

        ArchiverFactory.createArchiver(ArchiveFormat.TAR, CompressionType.GZIP)
                .extract(slsTgz, extracted)

        return new File(extracted, "myService-1.0.0")
    }
}
