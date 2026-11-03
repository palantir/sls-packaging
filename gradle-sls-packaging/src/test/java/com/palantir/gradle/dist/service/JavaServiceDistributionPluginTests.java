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
package com.palantir.gradle.dist.service;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableMap;
import com.palantir.gradle.dist.SlsManifest;
import com.palantir.gradle.dist.service.tasks.LaunchConfig;
import com.palantir.gradle.dist.service.utils.ExecUtils;
import com.palantir.gradle.dist.service.utils.TestUtils;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.GradleProject;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@GradlePluginTests
class JavaServiceDistributionPluginTests {
    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper(new YAMLFactory()).registerModule(new GuavaModule());

    private static final String EXTERNAL_JAR = new File("src/test/resources/external.jar").getAbsolutePath();

    @Test
    void produce_distribution_bundle_and_check_start_stop_restart_check_behavior(
            GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            distribution {
                checkArgs 'healthcheck'
            }
            """);

        rootProject.file("var/conf/launcher-custom.yml").overwrite("""
            configType: java
            configVersion: 1
            jvmOpts:
              - '-Dcustom.property=myCustomValue'
            """);

        rootProject.mainSourceSet().java().writeClass("""
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
            """);

        InvocationResult result =
                gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        assertThat(result).task(":createCheckScript").succeeded();

        // try all the service commands
        assertThat(ExecUtils.execWithExitCode(rootProject, "dist/service-name-0.0.1/service/bin/init.sh", "start"))
                .isEqualTo(0);
        // wait for the Java process to start up and emit output
        Thread.sleep(1000);
        assertThat(rootProject
                        .file("dist/service-name-0.0.1/var/log/startup.log")
                        .text())
                .contains("Test started\n");
        assertThat(ExecUtils.execWithExitCode(rootProject, "dist/service-name-0.0.1/service/bin/init.sh", "start"))
                .isEqualTo(0);
        assertThat(ExecUtils.execWithExitCode(rootProject, "dist/service-name-0.0.1/service/bin/init.sh", "status"))
                .isEqualTo(0);
        assertThat(ExecUtils.execWithExitCode(rootProject, "dist/service-name-0.0.1/service/bin/init.sh", "restart"))
                .isEqualTo(0);
        assertThat(ExecUtils.execWithExitCode(rootProject, "dist/service-name-0.0.1/service/bin/init.sh", "stop"))
                .isEqualTo(0);

        String checkOutput =
                ExecUtils.execWithOutput(rootProject, "dist/service-name-0.0.1/service/bin/init.sh", "check");
        assertThat(checkOutput).containsPattern("Checking health of 'service-name'\\.\\.\\.\\s+Healthy");

        String monitoringCheckOutput =
                ExecUtils.execWithOutput(rootProject, "dist/service-name-0.0.1/service/monitoring/bin/check.sh");
        assertThat(monitoringCheckOutput).containsPattern("Checking health of 'service-name'\\.\\.\\.\\s+Healthy");
    }

    @Test
    void packaging_tasks_re_run_after_version_change(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            distribution {
                enableManifestClasspath true
            }
            """);

        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {
                public static void main(String[] args) {
                    while(true);
                }
            }
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();
        rootProject.buildGradle().append("""
            version '0.0.2'
            """);

        InvocationResult result =
                gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();
        assertThat(result).task(":createCheckScript").upToDate();
        assertThat(result).task(":createInitScript").upToDate();
        assertThat(result).task(":createLaunchConfig").succeeded();
        assertThat(result).task(":createManifest").succeeded();
        assertThat(result).task(":manifestClasspathJar").succeeded();
        assertThat(result).task(":distTar").succeeded();

        assertThat(ExecUtils.execWithExitCode(rootProject, "dist/service-name-0.0.2/service/bin/init.sh", "start"))
                .isEqualTo(0);
        assertThat(ExecUtils.execWithExitCode(rootProject, "dist/service-name-0.0.2/service/bin/init.sh", "stop"))
                .isEqualTo(0);
    }

    @Test
    void produce_distribution_bundle_and_check_var_log_and_var_run_are_excluded(
            GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);

        rootProject.file("var/log/service-name.log").createEmpty();
        rootProject.file("var/run/service-name.pid").createEmpty();
        rootProject.file("var/conf/service-name.yml").createEmpty();

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        rootProject.file("dist/service-name-0.0.1").assertThat().exists();
        rootProject.file("dist/service-name-0.0.1/var/log").assertThat().doesNotExist();
        rootProject.file("dist/service-name-0.0.1/var/run").assertThat().doesNotExist();
        rootProject
                .file("dist/service-name-0.0.1/var/conf/service-name.yml")
                .assertThat()
                .exists();
    }

    @Test
    void produce_distribution_bundle_and_check_var_data_tmp_is_created_and_used_for_temporary_files(
            GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            import java.nio.file.Files;
            import java.io.IOException;
            public class Test {
                public static void main(String[] args) throws IOException {
                    Files.write(Files.createTempFile("prefix", "suffix"), "temp content".getBytes());
                }
            }
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        ExecUtils.execAllowFail(rootProject, "dist/service-name-0.0.1/service/bin/init.sh", "start");
        Thread.sleep(1000);
        File[] tmpFiles = rootProject
                .file("dist/service-name-0.0.1/var/data/tmp")
                .path()
                .toFile()
                .listFiles();
        assertThat(tmpFiles).hasSize(1);
        assertThat(tmpFiles[0]).hasContent("temp content");
    }

    @Test
    void produce_distribution_bundle_with_custom_exclude_set(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
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
            """);

        createUntarTask(rootProject);

        rootProject.file("var/log/service-name.log").createEmpty();
        rootProject.file("var/data/database").createEmpty();
        rootProject.file("var/conf/service-name.yml").createEmpty();

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        rootProject.file("dist/service-name-0.0.1/var/log").assertThat().doesNotExist();
        rootProject
                .file("dist/service-name-0.0.1/var/data/database")
                .assertThat()
                .doesNotExist();
        rootProject
                .file("dist/service-name-0.0.1/var/conf/service-name.yml")
                .assertThat()
                .exists();
    }

    @Test
    void produce_distribution_bundle_with_a_non_string_version_object(GradleInvoker gradle, RootProject rootProject)
            throws Exception {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
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
            """);

        createUntarTask(rootProject);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        SlsManifest manifest = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/deployment/manifest.yml")
                        .path()
                        .toFile(),
                SlsManifest.class);
        assertThat(manifest.productVersion()).isEqualTo("0.0.1");
    }

    @Test
    void manifest_file_contains_expected_fields(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        Map<String, Object> manifest = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/deployment/manifest.yml")
                        .path()
                        .toFile(),
                new TypeReference<Map<String, Object>>() {});
        assertThat(manifest.get("manifest-version")).isEqualTo("1.0");
        assertThat(manifest.get("product-group")).isEqualTo("service-group");
        assertThat(manifest.get("product-name")).isEqualTo("service-name");
        assertThat(manifest.get("product-version")).isEqualTo("0.0.1");
        assertThat(manifest.get("product-type")).isEqualTo("service.v1");

        @SuppressWarnings("unchecked")
        Map<String, Object> extensions = (Map<String, Object>) manifest.get("extensions");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> foo = (Map<String, List<String>>) extensions.get("foo");
        assertThat(foo.get("bar")).containsExactly("1", "2");
    }

    @Test
    void can_specify_service_dependencies(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
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
            """);
        rootProject.file("product-dependencies.lock").overwrite("""
            # Run ./gradlew writeProductDependenciesLocks to regenerate this file
            product-id: service-group:service-name
            group1:name1 (1.0.0, 1.3.x)
            group2:name2 (1.0.0, 1.x.x)
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        Map<String, Object> manifest = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/deployment/manifest.yml")
                        .path()
                        .toFile(),
                new TypeReference<Map<String, Object>>() {});

        @SuppressWarnings("unchecked")
        Map<String, Object> extensions = (Map<String, Object>) manifest.get("extensions");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> productDeps = (List<Map<String, String>>) extensions.get("product-dependencies");

        Map<String, String> dep1 = productDeps.get(0);
        assertThat(dep1.get("product-group")).isEqualTo("group1");
        assertThat(dep1.get("product-name")).isEqualTo("name1");
        assertThat(dep1.get("minimum-version")).isEqualTo("1.0.0");
        assertThat(dep1.get("maximum-version")).isEqualTo("1.3.x");
        assertThat(dep1.get("recommended-version")).isEqualTo("1.2.1");

        Map<String, String> dep2 = productDeps.get(1);
        assertThat(dep2.get("product-group")).isEqualTo("group2");
        assertThat(dep2.get("product-name")).isEqualTo("name2");
        assertThat(dep2.get("minimum-version")).isEqualTo("1.0.0");
    }

    @Test
    void cannot_specify_service_dependencies_with_invalid_versions_with_closure_constructor(
            GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            distribution {
                productDependency {
                    productName = "name1"
                    productGroup = "group1"
                    minimumVersion = "1.0.x"
                    maximumVersion = "2.0.0"
                }
            }
            """);

        InvocationResult result = gradle.withArgs("distTar").buildsWithFailure();

        assertThat(result).output().contains("minimumVersion must be an SLS version");
    }

    @Test
    void produce_distribution_bundle_with_files_in_deployment(GradleInvoker gradle, RootProject rootProject)
            throws Exception {
        createUntarBuildFile(rootProject);

        String deploymentConfiguration = "log: service-name.log";
        rootProject.file("deployment/manifest.yml").overwrite("invalid manifest");
        rootProject.file("deployment/configuration.yml").overwrite(deploymentConfiguration);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        // clobbers deployment/manifest.yml
        SlsManifest manifest = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/deployment/manifest.yml")
                        .path()
                        .toFile(),
                SlsManifest.class);
        assertThat(manifest.productName()).isEqualTo("service-name");

        // check files in deployment/ copied successfully
        String actualConfiguration = rootProject
                .file("dist/service-name-0.0.1/deployment/configuration.yml")
                .text();
        assertThat(actualConfiguration).isEqualTo(deploymentConfiguration);
    }

    @Test
    void allows_another_task_to_produce_configuration_yml(GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);

        rootProject.buildGradle().append("""
            task createConfigurationYml {
                outputs.file('build/some-place/configuration.yml')

                doFirst {
                    file('build/some-place/configuration.yml').text = 'custom: yml'
                }
            }

            distribution {
                configurationYml.fileProvider(tasks.named('createConfigurationYml').map { it.outputs.files.singleFile })
            }
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        String actualConfiguration = rootProject
                .file("dist/service-name-0.0.1/deployment/configuration.yml")
                .text();
        assertThat(actualConfiguration).isEqualTo("custom: yml");
    }

    @Test
    void errors_out_if_the_custom_configuration_yml_location_is_not_a_file_called_configuration_yml(
            GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);

        rootProject.buildGradle().append("""
            task createConfigurationYml {
                outputs.file('build/some-place/something-else.yml')

                doFirst {
                    file('build/some-place/something-else.yml').text = 'custom: yml'
                }
            }

            distribution {
                configurationYml.fileProvider(tasks.named('createConfigurationYml').map { it.outputs.files.singleFile })
            }
            """);

        InvocationResult output =
                gradle.withArgs(":build", ":distTar", ":untar").buildsWithFailure();

        assertThat(output).output().contains("must be called configuration.yml");
    }

    @Test
    void produce_distribution_bundle_with_start_script_that_passes_default_jvm_options(
            GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        String startScript = rootProject
                .file("dist/service-name-0.0.1/service/bin/service-name")
                .text();
        assertThat(startScript).contains("DEFAULT_JVM_OPTS='\"-Xmx4M\" \"-Djavax.net.ssl.trustStore=truststore.jks\"'");
    }

    @Test
    void produce_distribution_bundle_that_populates_launcher_static_yml_and_launcher_check_yml(
            GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);

        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
                javaHome 'foo'
                args 'myArg1', 'myArg2'
                checkArgs 'myCheckArg1', 'myCheckArg2'
                env "key1": "val1",
                    "key2": "val2"
            }
            """, EXTERNAL_JAR);

        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo expectedStaticConfig = LaunchConfig.LaunchConfigInfo.builder()
                .mainClass("test.Test")
                .serviceName("service-name")
                .javaHome("foo")
                .args(List.of("myArg1", "myArg2"))
                .classpath(List.of("service/lib/internal-0.0.1.jar", "service/lib/external.jar"))
                .jvmOpts(List.of(
                        "-XX:+CrashOnOutOfMemoryError",
                        "-Djava.io.tmpdir=var/data/tmp",
                        "-Djna.tmpdir=var/data/tmp",
                        "-XX:ErrorFile=var/log/hs_err_pid%p.log",
                        "-XX:HeapDumpPath=var/log",
                        "-Dsun.net.inetaddr.ttl=10",
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+IgnoreUnrecognizedVMOptions",
                        "-XX:NativeMemoryTracking=summary",
                        "-XX:FlightRecorderOptions=stackdepth=256",
                        "-XX:UseAVX=2",
                        "-XX:CompileCommand=exclude,sun/security/ssl/SSLEngineInputRecord.decodeInputRecord",
                        "-XX:-UseBiasedLocking",
                        "-XX:+UseParallelGC",
                        "-Xmx4M",
                        "-Djavax.net.ssl.trustStore=truststore.jks"))
                .env(Map.of(
                        "MALLOC_ARENA_MAX", "4",
                        "key1", "val1",
                        "key2", "val2"))
                .dirs(List.of("var/data/tmp"))
                .build();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);

        LaunchConfig.LaunchConfigInfo expectedCheckConfig = LaunchConfig.LaunchConfigInfo.builder()
                .mainClass(actualStaticConfig.mainClass())
                .serviceName(actualStaticConfig.serviceName())
                .javaHome(actualStaticConfig.javaHome())
                .args(List.of("myCheckArg1", "myCheckArg2"))
                .classpath(actualStaticConfig.classpath())
                .jvmOpts(List.of(
                        "-XX:+CrashOnOutOfMemoryError",
                        "-Djava.io.tmpdir=var/data/tmp",
                        "-Djna.tmpdir=var/data/tmp",
                        "-XX:ErrorFile=var/log/hs_err_pid%p.log",
                        "-XX:HeapDumpPath=var/log",
                        "-Dsun.net.inetaddr.ttl=10",
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+IgnoreUnrecognizedVMOptions",
                        "-XX:NativeMemoryTracking=summary",
                        "-XX:FlightRecorderOptions=stackdepth=256",
                        "-XX:UseAVX=2",
                        "-Xmx4M",
                        "-Djavax.net.ssl.trustStore=truststore.jks"))
                .env(LaunchConfig.defaultEnvironment)
                .dirs(actualStaticConfig.dirs())
                .build();

        LaunchConfig.LaunchConfigInfo actualCheckConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-check.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);

        assertThat(actualCheckConfig).isEqualTo(expectedCheckConfig);
        assertThat(actualStaticConfig).isEqualTo(expectedStaticConfig);
    }

    @Test
    void produce_distribution_bundle_that_populates_launcher_static_yml_and_launcher_check_yml_with_bundled_jdk(
            GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);

        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
                jdks.put(JavaVersion.VERSION_11, fileTree('build/fake-jdk'))
                javaHome 'foo'
                args 'myArg1', 'myArg2'
                checkArgs 'myCheckArg1', 'myCheckArg2'
                env "key1": "val1",
                    "key2": "val2"
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo expectedStaticConfig = LaunchConfig.LaunchConfigInfo.builder()
                .mainClass("test.Test")
                .serviceName("service-name")
                .javaHome("foo")
                .args(List.of("myArg1", "myArg2"))
                .classpath(List.of("service/lib/internal-0.0.1.jar", "service/lib/external.jar"))
                .jvmOpts(List.of(
                        "-XX:+CrashOnOutOfMemoryError",
                        "-Djava.io.tmpdir=var/data/tmp",
                        "-Djna.tmpdir=var/data/tmp",
                        "-XX:ErrorFile=var/log/hs_err_pid%p.log",
                        "-XX:HeapDumpPath=var/log",
                        "-Dsun.net.inetaddr.ttl=10",
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+IgnoreUnrecognizedVMOptions",
                        "-XX:NativeMemoryTracking=summary",
                        "-XX:FlightRecorderOptions=stackdepth=256",
                        "-XX:UseAVX=2",
                        "-XX:-UseBiasedLocking",
                        "-XX:+UseParallelGC",
                        "-Xmx4M",
                        "-Djavax.net.ssl.trustStore=truststore.jks"))
                .env(ImmutableMap.<String, String>builder()
                        .putAll(LaunchConfig.defaultEnvironment)
                        .putAll(Map.of(
                                "key1", "val1",
                                "key2", "val2",
                                "JAVA_11_HOME", "service/service-name-jdks/jdk11"))
                        .buildOrThrow())
                .dirs(List.of("var/data/tmp"))
                .build();
        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);

        LaunchConfig.LaunchConfigInfo expectedCheckConfig = LaunchConfig.LaunchConfigInfo.builder()
                .mainClass(actualStaticConfig.mainClass())
                .serviceName(actualStaticConfig.serviceName())
                .javaHome(actualStaticConfig.javaHome())
                .args(List.of("myCheckArg1", "myCheckArg2"))
                .classpath(actualStaticConfig.classpath())
                .jvmOpts(List.of(
                        "-XX:+CrashOnOutOfMemoryError",
                        "-Djava.io.tmpdir=var/data/tmp",
                        "-Djna.tmpdir=var/data/tmp",
                        "-XX:ErrorFile=var/log/hs_err_pid%p.log",
                        "-XX:HeapDumpPath=var/log",
                        "-Dsun.net.inetaddr.ttl=10",
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+IgnoreUnrecognizedVMOptions",
                        "-XX:NativeMemoryTracking=summary",
                        "-XX:FlightRecorderOptions=stackdepth=256",
                        "-XX:UseAVX=2",
                        "-Xmx4M",
                        "-Djavax.net.ssl.trustStore=truststore.jks"))
                .env(LaunchConfig.defaultEnvironment)
                .dirs(actualStaticConfig.dirs())
                .build();

        LaunchConfig.LaunchConfigInfo actualCheckConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-check.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualCheckConfig).isEqualTo(expectedCheckConfig);
        assertThat(actualStaticConfig).isEqualTo(expectedStaticConfig);
    }

    @Test
    void produce_distribution_with_java_8_gc_logging(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaHome 'foo'
                addJava8GcLogging true
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo expectedStaticConfig = LaunchConfig.LaunchConfigInfo.builder()
                .mainClass("test.Test")
                .serviceName("service-name")
                .javaHome("foo")
                .classpath(List.of("service/lib/internal-0.0.1.jar", "service/lib/external.jar"))
                .jvmOpts(List.of(
                        "-XX:+CrashOnOutOfMemoryError",
                        "-Djava.io.tmpdir=var/data/tmp",
                        "-Djna.tmpdir=var/data/tmp",
                        "-XX:ErrorFile=var/log/hs_err_pid%p.log",
                        "-XX:HeapDumpPath=var/log",
                        "-Dsun.net.inetaddr.ttl=10",
                        "-XX:+UnlockDiagnosticVMOptions",
                        "-XX:+IgnoreUnrecognizedVMOptions",
                        "-XX:NativeMemoryTracking=summary",
                        "-XX:FlightRecorderOptions=stackdepth=256",
                        "-XX:UseAVX=2",
                        "-XX:+PrintGCDateStamps",
                        "-XX:+PrintGCDetails",
                        "-XX:-TraceClassUnloading",
                        "-XX:+UseGCLogFileRotation",
                        "-XX:GCLogFileSize=10M",
                        "-XX:NumberOfGCLogFiles=10",
                        "-Xloggc:var/log/gc-%t-%p.log",
                        "-verbose:gc",
                        "-XX:-UseBiasedLocking",
                        "-XX:+UseParallelGC",
                        "-Xmx4M",
                        "-Djavax.net.ssl.trustStore=truststore.jks"))
                .dirs(List.of("var/data/tmp"))
                .env(Map.of("MALLOC_ARENA_MAX", "4"))
                .build();
        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig).isEqualTo(expectedStaticConfig);
    }

    @Test
    void respects_java_version(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 14
                gc 'response-time'
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts())
                .containsAll(List.of(
                        "-XX:+UseShenandoahGC",
                        "-XX:+ExplicitGCInvokesConcurrent",
                        "-XX:+ClassUnloadingWithConcurrentMark"));
    }

    @Test
    void uses_generational_zgc_for_jdk_21(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 21
                gc 'response-time'
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts())
                .containsAll(List.of("-XX:+UseZGC", "-XX:+ZGenerational", "-XX:+ExplicitGCInvokesConcurrent"));
    }

    @Test
    void jdk_21_uses_default_avx_level(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 21
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts().stream().noneMatch(opt -> opt.contains("UseAVX")))
                .isTrue();
    }

    // Compact object headers (JEP 519) disabled pending resolution of https://bugs.openjdk.org/browse/JDK-8380060
    @Test
    void jdk_25_does_not_enable_compact_object_headers(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 25
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts().stream().noneMatch(opt -> opt.contains("UseCompactObjectHeaders")))
                .isTrue();
    }

    @Test
    void jdk_24_does_not_enable_compact_object_headers(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 24
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts().stream().noneMatch(opt -> opt.contains("UseCompactObjectHeaders")))
                .isTrue();
    }

    @Test
    void produce_distribution_bundle_that_populates_check_sh(GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            distribution {
                checkArgs 'healthcheck', 'var/conf/service.yml'
            }
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        rootProject
                .file("dist/service-name-0.0.1/service/monitoring/bin/check.sh")
                .assertThat()
                .exists();
    }

    @Test
    void produces_manifest_classpath_jar_and_windows_start_script_with_no_classpath_length_limitations(
            GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.settingsGradle().rootProjectName("root-project");

        rootProject.buildGradle().append("""
            distribution {
                enableManifestClasspath true
            }
            dependencies {
              implementation "com.google.guava:guava:19.0"
            }
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        String startScript = rootProject
                .file("dist/service-name-0.0.1/service/bin/service-name.bat")
                .text();
        assertThat(startScript).contains("-manifest-classpath-0.0.1.jar");
        assertThat(startScript).doesNotContain("-classpath \"%CLASSPATH%\"");

        Optional<File> classpathJar =
                TestUtils.findJarInLibDirectory(rootProject, "0.0.1", ".*-manifest-classpath-0\\.0\\.1\\.jar");
        assertThat(classpathJar).isPresent();

        String zipManifest = TestUtils.readFromZip(classpathJar.get(), "META-INF/MANIFEST.MF")
                .replace("\r\n ", "");
        assertThat(zipManifest).contains("Class-Path: ");
        assertThat(zipManifest)
                .as("the project's own jar should be listed before its runtime dependencies on the Class-Path")
                .containsSubsequence("root-project-0.0.1.jar", "guava-19.0.jar");
        assertThat(zipManifest).doesNotContain("root-project-manifest-classpath-0.0.1.jar");
    }

    @Test
    void manifest_classpath_lists_jars_in_the_same_order_as_the_non_manifest_classpath(
            GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.settingsGradle().rootProjectName("root-project");
        rootProject.buildGradle().append("""
            distribution {
                enableManifestClasspath findProperty('manifestClasspath') == 'true'
            }
            dependencies {
              implementation "com.google.guava:guava:19.0"
            }
            """);

        // Without the manifest classpath jar, the full classpath is written directly into the start script.
        gradle.withArgs(":distTar", ":untar", "-PmanifestClasspath=false").buildsSuccessfully();
        List<String> nonManifestClasspath =
                TestUtils.extractClasspathEntriesFromScript(rootProject, "0.0.1", "service-name").stream()
                        .map(JavaServiceDistributionPluginTests::jarFileName)
                        .toList();

        // With the manifest classpath jar, that same classpath instead lives in the jar's Class-Path attribute.
        gradle.withArgs(":distTar", ":untar", "-PmanifestClasspath=true").buildsSuccessfully();
        Optional<File> classpathJar =
                TestUtils.findJarInLibDirectory(rootProject, "0.0.1", ".*-manifest-classpath-0\\.0\\.1\\.jar");
        assertThat(classpathJar).isPresent();
        List<String> manifestClasspath;
        try (JarFile jarFile = new JarFile(classpathJar.get())) {
            String classPathAttribute =
                    jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            manifestClasspath = Arrays.stream(classPathAttribute.split(" "))
                    .map(JavaServiceDistributionPluginTests::jarFileName)
                    .toList();
        }

        assertThat(manifestClasspath)
                .as("the manifest Class-Path should list the same jars in the same order as the plain classpath")
                .containsExactlyElementsOf(nonManifestClasspath);
    }

    private static String jarFileName(String classpathEntry) {
        return Path.of(classpathEntry).getFileName().toString();
    }

    @Test
    void does_not_produce_manifest_classpath_jar_when_disabled_in_extension(
            GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        String startScript = rootProject
                .file("dist/service-name-0.0.1/service/bin/service-name.bat")
                .text();
        assertThat(startScript).doesNotContain("-manifest-classpath-0.1.jar");
        assertThat(startScript).contains("-classpath \"%CLASSPATH%\"");

        // Check that the manifest classpath JAR is not present
        assertThat(TestUtils.hasJarInLibDirectory(rootProject, "0.0.1", ".*-manifest-classpath-0\\.1\\.jar"))
                .isFalse();
    }

    @Test
    void disttar_artifact_name_is_set_during_appropriate_lifecycle_events(
            GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
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
            """);

        gradle.withArgs(":tasks").buildsSuccessfully();
    }

    @Test
    void exposes_an_artifact_through_the_sls_configuration(GradleInvoker gradle, SubProject parent, SubProject child) {
        parent.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        parent.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            version '0.0.1'
            distribution {
                serviceName "my-service"
                mainClass "dummy.service.MainClass"
                args "hello"
            }
            """);

        child.buildGradle().append("""
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
            """);

        InvocationResult buildResult = gradle.withArgs(":child:untar").buildsSuccessfully();

        assertThat(buildResult).task(":parent:distTar").succeeded();
        child.file("build/exploded/my-service-0.0.1/deployment/manifest.yml")
                .assertThat()
                .exists();
    }

    @Test
    void exposes_an_artifact_via_dependency_with_sls_dist_usage(
            GradleInvoker gradle, SubProject producer, SubProject consumer) {
        producer.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        producer.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            version '0.0.1'
            distribution {
                serviceName "my-service"
                mainClass "dummy.service.MainClass"
                args "hello"
            }
            """);

        consumer.buildGradle().append("""
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
            """);

        InvocationResult buildResult = gradle.withArgs(":consumer:untar").buildsSuccessfully();

        assertThat(buildResult).task(":producer:distTar").succeeded();
        consumer.file("build/exploded/my-service-0.0.1/deployment/manifest.yml")
                .assertThat()
                .exists();
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
    @Test
    void dist_project_can_be_resolved_through_plain_dependency_when_gcv_is_applied(
            GradleInvoker gradle, RootProject rootProject, SubProject dist) {
        rootProject.buildGradle().plugins().add("com.palantir.consistent-versions");

        rootProject.buildGradle().append("""
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
            """);

        dist.buildGradle().plugins().add("com.palantir.sls-java-service-distribution");

        dist.buildGradle().append("""
            version '0.0.1'
            distribution {
                serviceName "my-asset"
                mainClass "dummy.service.MainClass"
                args "hello"
            }
            """);

        gradle.withArgs("--write-locks").buildsSuccessfully();

        gradle.withArgs(":verify").buildsSuccessfully();
    }

    @Test
    void fails_when_asset_and_service_plugins_are_both_applied(GradleInvoker gradle, RootProject rootProject) {
        rootProject
                .buildGradle()
                .plugins()
                .add("com.palantir.sls-asset-distribution")
                .add("com.palantir.sls-java-service-distribution");

        InvocationResult result = gradle.withArgs(":tasks").buildsWithFailure();

        assertThat(result)
                .output()
                .contains("The plugins 'com.palantir.sls-asset-distribution' and "
                        + "'com.palantir.sls-java-service-distribution' cannot be used in the same Gradle project.");
    }

    @Test
    void uses_the_runtimeclasspath_so_api_and_implementation_configurations_work_with_java_library_plugin(
            GradleInvoker gradle, SubProject parent, SubProject child) throws Exception {
        parent.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        parent.buildGradle().append("""
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
            """);

        createUntarTask(parent);

        child.buildGradle().plugins().add("java-library");

        child.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            dependencies {
                api "com.google.guava:guava:19.0"
                implementation "com.google.code.findbugs:annotations:3.0.1"
            }
            """);

        gradle.withArgs(":parent:build", ":parent:distTar", ":parent:untar").buildsSuccessfully();

        // Verify required JARs are present
        assertThat(TestUtils.hasJarInLibDirectory(parent, "0.0.1", "annotations-3\\.0\\.1\\.jar"))
                .isTrue();
        assertThat(TestUtils.hasJarInLibDirectory(parent, "0.0.1", "guava-19\\.0\\.jar"))
                .isTrue();
        assertThat(TestUtils.hasJarInLibDirectory(parent, "0.0.1", "mockito-core-2\\.7\\.22\\.jar"))
                .isTrue();
        assertThat(TestUtils.hasJarInLibDirectory(parent, "0.0.1", "main")).isFalse();

        // verify start scripts
        List<String> classpathEntries = TestUtils.extractClasspathEntriesFromScript(parent, "0.0.1", "service-name");
        assertThat(classpathEntries).anyMatch(entry -> entry.contains("/lib/annotations-3.0.1.jar"));
        assertThat(classpathEntries).anyMatch(entry -> entry.contains("/lib/guava-19.0.jar"));
        assertThat(classpathEntries).anyMatch(entry -> entry.contains("/lib/mockito-core-2.7.22.jar"));

        // verify launcher YAML files
        LaunchConfig.LaunchConfigInfo launcherCheck = OBJECT_MAPPER.readValue(
                parent.file("dist/service-name-0.0.1/service/bin/launcher-check.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(launcherCheck.classpath())
                .anyMatch(cp -> cp.contains("/lib/annotations-3.0.1.jar"))
                .anyMatch(cp -> cp.contains("/lib/guava-19.0.jar"))
                .anyMatch(cp -> cp.contains("/lib/mockito-core-2.7.22.jar"));

        LaunchConfig.LaunchConfigInfo launcherStatic = OBJECT_MAPPER.readValue(
                parent.file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(launcherStatic.classpath())
                .anyMatch(cp -> cp.contains("/lib/annotations-3.0.1.jar"))
                .anyMatch(cp -> cp.contains("/lib/guava-19.0.jar"))
                .anyMatch(cp -> cp.contains("/lib/mockito-core-2.7.22.jar"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--write-locks", "writeProductDependenciesLocks"})
    void docker_can_resolve_inter_project_product_dependencies(
            String writeLocksTask, GradleInvoker gradle, RootProject rootProject, SubProject first, SubProject second) {
        rootProject.buildGradle().append("""
            allprojects {
                group = 'group'
                version = '1.0.0'
            }
            """);
        rootProject.buildGradle().plugins().add("com.palantir.docker-compose");

        first.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        first.buildGradle().append("""
            distribution {
                mainClass "dummy.service.MainClass"
                productDependency {
                    productGroup = 'group'
                    productName = 'second-product'
                    minimumVersion = project.version
                }
            }
            """);

        second.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        second.buildGradle().append("""
            distribution {
                serviceName = 'second-product'
                mainClass "dummy.service.MainClass"
            }
            """);

        gradle.withArgs(writeLocksTask).buildsSuccessfully();

        // We're just using generateDockerCompose as it conveniently resolves the 'docker' configuration for us
        // Which in turn, conveniently depends on all subprojects' `productDependencies` configurations
        rootProject.file("docker-compose.yml.template").createEmpty();

        gradle.withArgs("generateDockerCompose").buildsSuccessfully();
    }

    @Test
    void uses_the_runtimeclasspath_in_manifest_jar(GradleInvoker gradle, SubProject parent, SubProject child)
            throws Exception {
        parent.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        parent.buildGradle().append("""
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
            """);

        createUntarTask(parent);

        child.buildGradle().plugins().add("java-library");

        child.buildGradle().append("""
            repositories {
                mavenCentral()
            }
            dependencies {
                api "com.google.guava:guava:19.0"
                implementation "com.google.code.findbugs:annotations:3.0.1"
            }
            """);

        gradle.withArgs(":parent:build", ":parent:distTar", ":parent:untar").buildsSuccessfully();

        // Verify required JARs are present
        assertThat(TestUtils.hasJarInLibDirectory(parent, "0.0.1", "annotations-3\\.0\\.1\\.jar"))
                .isTrue();
        assertThat(TestUtils.hasJarInLibDirectory(parent, "0.0.1", "guava-19\\.0\\.jar"))
                .isTrue();
        assertThat(TestUtils.hasJarInLibDirectory(parent, "0.0.1", "mockito-core-2\\.7\\.22\\.jar"))
                .isTrue();
        assertThat(TestUtils.hasJarInLibDirectory(parent, "0.0.1", "main")).isFalse();

        // Find the manifest classpath JAR
        Optional<File> classpathJar =
                TestUtils.findJarInLibDirectory(parent, "0.0.1", ".*-manifest-classpath-0\\.0\\.1\\.jar");
        assertThat(classpathJar).isPresent();

        // verify META-INF/MANIFEST.MF
        String manifestContents = TestUtils.readFromZip(classpathJar.get(), "META-INF/MANIFEST.MF");
        String normalizedManifest = manifestContents.replace("\r\n ", "").replace("\n ", "");
        assertThat(normalizedManifest).contains("annotations-3.0.1.jar");
        assertThat(normalizedManifest).contains("guava-19.0.jar");
        assertThat(normalizedManifest).contains("mockito-core-2.7.22.jar");
        assertThat(normalizedManifest).doesNotContain("main");

        // verify start scripts
        List<String> classpathEntries = TestUtils.extractClasspathEntriesFromScript(parent, "0.0.1", "service-name");
        assertThat(classpathEntries).anyMatch(entry -> entry.contains("-manifest-classpath-0.0.1.jar"));

        // verify launcher YAML files
        LaunchConfig.LaunchConfigInfo launcherCheck = OBJECT_MAPPER.readValue(
                parent.file("dist/service-name-0.0.1/service/bin/launcher-check.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(launcherCheck.classpath()).anyMatch(cp -> cp.contains("-manifest-classpath-0.0.1.jar"));

        LaunchConfig.LaunchConfigInfo launcherStatic = OBJECT_MAPPER.readValue(
                parent.file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(launcherStatic.classpath()).anyMatch(cp -> cp.contains("-manifest-classpath-0.0.1.jar"));
    }

    @Test
    void project_class_files_do_not_appear_in_output_lib_directory(GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {
                public static void main(String[] args) {
                    while(true);
                }
            }
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        rootProject
                .file("dist/service-name-0.0.1/service/lib/com/test/Test.class")
                .assertThat()
                .doesNotExist();
    }

    @Test
    void adds_initiating_occupancy_fraction_gc_profile_jvm_settings(GradleInvoker gradle, RootProject rootProject)
            throws Exception {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
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
            """);

        createUntarTask(rootProject);

        gradle.withArgs(":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts())
                .containsAll(List.of(
                        "-XX:+UseParNewGC", "-XX:+UseConcMarkSweepGC", "-XX:CMSInitiatingOccupancyFraction=75"));
    }

    @Test
    void adds_max_gc_pause_millis_gc_profile_jvm_settings(GradleInvoker gradle, RootProject rootProject)
            throws Exception {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
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
            """);

        createUntarTask(rootProject);

        gradle.withArgs(":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts())
                .containsAll(List.of("-XX:+UseG1GC", "-XX:+UseNUMA", "-XX:MaxGCPauseMillis=1234"));
    }

    @Test
    void gc_profile_null_configuration_closure(GradleInvoker gradle, RootProject rootProject) throws Exception {
        rootProject.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            version '0.0.1'

            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
                gc 'hybrid'
            }
            """);

        createUntarTask(rootProject);

        gradle.withArgs(":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts())
                .containsAll(List.of("-XX:+UseG1GC", "-XX:+UseNUMA", "-XX:MaxGCPauseMillis=500"));
    }

    @Test
    void applies_java_agents(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies {
                implementation files("%s")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}\
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts()).contains("-javaagent:service/lib/agent/byte-buddy-agent-1.10.21.jar");
        rootProject
                .file("dist/service-name-0.0.1/service/lib/agent/byte-buddy-agent-1.10.21.jar")
                .assertThat()
                .exists();
    }

    @Test
    void fails_at_build_time_when_non_agent_jars_are_provided_as_agents(GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies {
                implementation files("%s")
                javaAgent files("%s")
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
            }
            """, EXTERNAL_JAR, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        InvocationResult result = gradle.withArgs(":distTar").buildsWithFailure();

        assertThat(result).output().contains("is not a java agent and contains no Premain-Class manifest entry");
    }

    @Test
    void exports_management_packages_on_new_javas(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts())
                .containsAll(List.of("--add-exports", "java.management/sun.management=ALL-UNNAMED"));
    }

    @Test
    void applies_exports_based_on_classpath_manifests(GradleInvoker gradle, RootProject rootProject) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Add-Exports", "jdk.compiler/com.sun.tools.javac.file");
        File testJar = rootProject.path().resolve("test.jar").toFile();
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(testJar.toPath()), manifest)) {
            // Just create the jar with the manifest
        }

        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies {
                implementation files("test.jar")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
            }
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualOpts = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);

        // Quick check
        assertThat(actualOpts.jvmOpts())
                .containsAll(List.of("--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"));

        // Verify args are set in the correct order
        int compilerPairIndex = actualOpts.jvmOpts().indexOf("jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED");
        assertThat(actualOpts.jvmOpts().get(compilerPairIndex - 1)).isEqualTo("--add-exports");
    }

    @Test
    void applies_opens_based_on_classpath_manifests(GradleInvoker gradle, RootProject rootProject) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Add-Opens", "jdk.compiler/com.sun.tools.javac.file");
        File testJar = rootProject.path().resolve("test.jar").toFile();
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(testJar.toPath()), manifest)) {
            // Just create the jar with the manifest
        }

        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies {
                implementation files("test.jar")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
            }
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualOpts = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);

        // Quick check
        assertThat(actualOpts.jvmOpts())
                .containsAll(List.of("--add-opens", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"));

        // Verify args are set in the correct order
        int compilerPairIndex = actualOpts.jvmOpts().indexOf("jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED");
        assertThat(actualOpts.jvmOpts().get(compilerPairIndex - 1)).isEqualTo("--add-opens");
    }

    @Test
    void applies_opens_based_on_classpath_manifests_for_manifest_classpaths(
            GradleInvoker gradle, RootProject rootProject) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Add-Opens", "jdk.compiler/com.sun.tools.javac.file");
        File testJar = rootProject.path().resolve("test.jar").toFile();
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(testJar.toPath()), manifest)) {
            // Just create the jar with the manifest
        }

        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies {
                implementation files("test.jar")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
                enableManifestClasspath true
            }
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualOpts = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);

        // Quick check
        assertThat(actualOpts.jvmOpts())
                .containsAll(List.of("--add-opens", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"));

        // Verify args are set in the correct order
        int compilerPairIndex = actualOpts.jvmOpts().indexOf("jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED");
        assertThat(actualOpts.jvmOpts().get(compilerPairIndex - 1)).isEqualTo("--add-opens");
    }

    @Test
    void handles_jars_with_no_manifest(GradleInvoker gradle, RootProject rootProject) {
        File testJar = rootProject.path().resolve("test.jar").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(testJar.toPath()))) {
            // Just create an empty zip
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies {
                implementation files("test.jar")
                javaAgent "net.bytebuddy:byte-buddy-agent:1.10.21"
            }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 17
            }
            """);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        InvocationResult result =
                gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        assertThat(result).task(":build").succeeded();
        assertThat(result).task(":distTar").succeeded();
        assertThat(result).task(":untar").succeeded();
    }

    @Test
    void can_resolve_go_java_launcher_binaries_through_gcv(GradleInvoker gradle, RootProject rootProject) {
        // Set a lower default version of go-java-launcher so we can verify that we pick up the higher version through
        // GCV
        rootProject
                .gradlePropertiesFile()
                .setProperty(JavaServiceDistributionPlugin.TEST_GO_JAVA_LAUNCHER_FALLBACK_VERSION_OVERRIDE, "1.17.0");

        String goJavaLauncherVersion = "1.18.0";

        rootProject
                .buildGradle()
                .plugins()
                .add("com.palantir.consistent-versions")
                .add("com.palantir.sls-java-service-distribution");

        rootProject.buildGradle().append("""
            repositories {
                mavenCentral()
            }

            version '0.0.1'
            distribution {
                serviceName 'service-name'
                mainClass 'test.Test'
            }
            """);

        rootProject.propertiesFile("versions.props").setProperty("com.palantir.launching:*", goJavaLauncherVersion);

        createUntarTask(rootProject);

        InvocationResult res =
                gradle.withArgs(":distTar", ":untar", "--write-locks").buildsSuccessfully();

        // Verify the test version is actually used
        assertThat(res).output().contains("using test only version override for go-java-launcher: 1.17.0");

        rootProject
                .file(String.format(
                        "dist/service-name-0.0.1/service/bin/go-java-launcher-%s/service/bin", goJavaLauncherVersion))
                .assertThat()
                .exists();
        rootProject
                .file(String.format(
                        "dist/service-name-0.0.1/service/bin/go-init-%s/service/bin", goJavaLauncherVersion))
                .assertThat()
                .exists();
    }

    @Test
    void enable_always_pre_touch(GradleInvoker gradle, RootProject rootProject) throws Exception {
        createUntarBuildFile(rootProject);
        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                enableAlwaysPreTouch()
            }
            """, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        LaunchConfig.LaunchConfigInfo actualStaticConfig = OBJECT_MAPPER.readValue(
                rootProject
                        .file("dist/service-name-0.0.1/service/bin/launcher-static.yml")
                        .path()
                        .toFile(),
                LaunchConfig.LaunchConfigInfo.class);
        assertThat(actualStaticConfig.jvmOpts())
                .containsAll(List.of("-XX:+AlwaysPreTouch", "-XX:+UseTransparentHugePages"));
    }

    @Test
    void produce_distribution_bundle_that_can_bundle_extra_jars(GradleInvoker gradle, RootProject rootProject) {
        createUntarBuildFile(rootProject);

        rootProject.buildGradle().append("""
            dependencies { implementation files("%s") }
            tasks.jar.archiveBaseName = "internal"
            distribution {
                javaVersion 11
                javaHome 'foo'
                extraFiles {
                    into('service/maven') {
                      from(files("%s"))
                    }
                }
            }
            """, EXTERNAL_JAR, EXTERNAL_JAR);
        rootProject.mainSourceSet().java().writeClass("""
            package test;
            public class Test {}
            """);

        gradle.withArgs(":build", ":distTar", ":untar").buildsSuccessfully();

        rootProject
                .file(String.format(
                        "dist/service-name-0.0.1/service/maven/%s",
                        Path.of(EXTERNAL_JAR).getFileName()))
                .assertThat()
                .exists();
    }

    private void createUntarBuildFile(GradleProject gradleProject) {
        gradleProject.buildGradle().plugins().add("java").add("com.palantir.sls-java-service-distribution");

        gradleProject.buildGradle().append("""
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
            """);

        createUntarTask(gradleProject);
    }

    private void createUntarTask(GradleProject project) {
        project.buildGradle().append("""
            // most convenient way to untar the dist is to use gradle
            task untar (type: Copy) {
                from { tarTree(tasks.distTar.outputs.files.singleFile) }
                into "dist"
                dependsOn distTar
                duplicatesStrategy = 'INCLUDE'
            }
            """);
    }
}
