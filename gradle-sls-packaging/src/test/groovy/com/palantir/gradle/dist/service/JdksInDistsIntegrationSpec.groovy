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
                mavenCentral()
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
        def jdkDir = new File(rootDir, "service/myService-1.0.0-jdks/jdk17")
        jdkDir.exists()

        def releaseFileText = new File(jdkDir, "release").text

        releaseFileText.contains 'its a jdk trust me'

        def launcherStatic = new File(rootDir, "service/bin/launcher-static.yml").text
        launcherStatic.contains 'javaHome: "service/myService-1.0.0-jdks/jdk17"'
        launcherStatic.contains '  JAVA_17_HOME: "service/myService-1.0.0-jdks/jdk17"'
    }

    def 'multiple jdks can exist in the dist'() {
        // language=gradle
        buildFile << '''
            distribution {
                javaVersion JavaVersion.VERSION_17
                jdks.put(JavaVersion.VERSION_11, fileTree('build/fake-jdk'))
                jdks.put(JavaVersion.VERSION_13, fileTree('build/fake-jdk'))
                jdks.put(JavaVersion.VERSION_17, fileTree('build/fake-jdk'))
            }
        '''.stripIndent(true)

        when:
        runTasksSuccessfully('distTar')

        then:
        def rootDir = extractDist()

        def launcherStatic = new File(rootDir, "service/bin/launcher-static.yml").text
        launcherStatic.contains 'javaHome: "service/myService-1.0.0-jdks/jdk17"'

        for (version in [11, 13, 17]) {
            def jdkDir = new File(rootDir, "service/myService-1.0.0-jdks/jdk" + version)
            assert jdkDir.exists()

            def releaseFileText = new File(jdkDir, "release").text

            assert releaseFileText.contains('its a jdk trust me')

            def envVarLine = "  JAVA_${version}_HOME: \"service/myService-1.0.0-jdks/jdk${version}\""
            assert launcherStatic.contains(envVarLine)
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
