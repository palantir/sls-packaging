Java Distribution Gradle Plugin
================================
[![Build Status](https://circleci.com/gh/palantir/gradle-java-distribution.svg?style=shield)](https://circleci.com/gh/palantir/gradle-java-distribution)
[![Coverage Status](https://coveralls.io/repos/github/palantir/gradle-java-distribution/badge.svg?branch=develop)](https://coveralls.io/github/palantir/gradle-java-distribution?branch=develop)
[![Gradle Plugins Release](https://api.bintray.com/packages/palantir/releases/gradle-java-distribution/images/download.svg)](https://plugins.gradle.org/plugin/com.palantir.java-distribution)

Similar to the standard application plugin, this plugin facilitates packaging
Gradle projects for easy distribution and execution. This distribution chooses
different packaging conventions that attempt to split immutable files from
mutable state and configuration.

In particular, this plugin packages a project into a common deployment structure
with a simple start script, daemonizing script, and, a manifest describing the
content of the package. The package will follow this structure:

    [service-name]-[service-version]/
        deployment/
            manifest.yml                      # simple package manifest
        service/
            bin/
                [service-name]                # Bash start script
                [service-name].bat            # Windows start script
                init.sh                       # daemonizing script
                darwin-amd64/go-java-launcher # Native Java launcher binary (MacOS)
                linux-amd64/go-java-launcher  # Native Java launcher binary (Linux)
                launcher-static.yml           # generated configuration for go-java-launcher
                launcher-check.yml            # generated configuration for check.sh go-java-launcher
            lib/
                [jars]
            monitoring/
                bin/ 
                    check.sh                  # monitoring script
        var/                                  # application configuration and data

The `service/bin/` directory contains both Gradle-generated launcher scripts (`[service-name]` and `[service-name].bat`)
and [go-java-launcher](https://github.com/palantir/go-java-launcher) launcher binaries.

Packages are produced as gzipped tar named `[service-name]-[project-version].sls.tgz`.

Usage
-----
Apply the plugin using standard Gradle convention:

    plugins {
        id 'com.palantir.java-distribution'
    }

Set the service name, main class, and optionally the arguments to pass to the
program for a default run configuration:

    distribution {
        serviceName 'my-service'
        mainClass 'com.palantir.foo.bar.MyServiceMainClass'
        args 'server', 'var/conf/my-service.yml'
    }

The `distribution` block offers the following options:

 * `serviceName` the name of this service, used to construct the final artifact's file name.
 * `mainClass` class containing the entry point to start the program.
 * (optional) `args` a list of arguments to supply when running `start`.
 * (optional) `checkArgs` a list of arguments to supply to the monitoring script, if omitted,
   no monitoring script will be generated.
 * (optional) `defaultJvmOpts` a list of default JVM options to set on the program.
 * (optional) `enableManifestClasspath` a boolean flag; if set to true, then the explicit Java
   classpath is omitted from the generated start scripts and instead inferred
   from a JAR file whose MANIFEST contains the classpath entries.
 * (optional) `excludeFromVar` a list of directories (relative to `${projectDir}/var`) to exclude from the distribution,
   defaulting to `['log', 'run']`.
   **Note**: this plugin will *always* create `var/data/tmp` in the resulting distribution to
   ensure the prescribed Java temp directory exists. Setting `data` for this option will still ensure
   nothing in `${projectDir}/var/data` is copied.
 * (optional) `javaHome` a fixed override for the `JAVA_HOME` environment variable that will
   be applied when `init.sh` is run.

#### JVM Options
The list of JVM options passed to the Java processes launched through a package's start-up scripts is obtained by
concatenating the following list of hard-coded *required options* and the list of options specified in
`distribution.defaultJvmOpts`:

Hard-coded required JVM options:
- `-Djava.io.tmpdir=var/data/tmp`: Allocates temporary files inside the application installation folder rather than on
  `/tmp`; the latter is often space-constrained on cloud hosts.

The `go-java-launcher` and `init.sh` launchers additionally append the list of JVM options specified in the
`var/conf/launcher-custom.yml` [configuration file](https://github.com/palantir/go-java-launcher). Note that later
options typically override earlier options (although this behavior is undefined and may be JVM-specific); this allows
users to override the hard-coded options.

#### Runtime environment variables
Environment variables can be configured through the `env` blocks of `launcher-static.yml` and `launcher-custom.yml` as
described in [configuration file](https://github.com/palantir/go-java-launcher). They are set by the launcher process
before the Java process is executed.

Packaging
---------
To create a compressed, gzipped tar file, run the `distTar` task.

As part of package creation, this plugin will create three shell scripts:

 * `service/bin/[service-name]`: a Gradle default start script for running
   the defined `mainClass`. This script is considered deprecated due to security issues with
   injectable Bash code; use the go-java-launcher binaries instead (see below).
 * `service/bin/<architecture>/go-java-launcher`: native binaries for executing the specified `mainClass`,
   configurable via `service/bin/launcher-static.yml` and `var/conf/launcher-custom.yml`.
 * `service/bin/init.sh`: a shell script to assist with daemonizing a JVM
   process. The script takes a single argument of `start`, `stop`, `console` or `status`.
   - `start`: On calls to `service/bin/init.sh start`,
     `service/bin/<architecture>/go-java-launcher` will be executed, disowned, and a pid file
     recorded in `var/run/[service-name].pid`.
   - `console`: like `start`, but does not background the process.
   - `status`: returns 0 when `var/run/[service-name].pid` exists and a
     process the id recorded in that file with a command matching the expected
     start command is found in the process table.
   - `stop`: if the process status is 0, issues a kill signal to the process.
 * `service/monitoring/bin/check.sh`: a no-argument shell script that returns `0` when
   a service is healthy and non-zero otherwise. This script is generated if and only if
   `checkArgs` is specified above, and will run the singular command defined by invoking
   `<mainClass> [checkArgs]` to obtain health status.


In addition to creating these scripts, this plugin will merge the entire
contents of `${projectDir}/service` and `${projectDir}/var` into the package.

The plugin also exposes the tar file as an artifact in the `sls` configuration, making it easy to
share the artifact between sibling Gradle projects. For example:

```groovy
configurations { tarballs }

dependencies {
    tarballs project(path: ':other-project', configuration: 'sls')
}
```

Running with Gradle
-------------------
To run the main class using Gradle, run the `run` task.

Tasks
-----
 * `distTar`: creates the gzipped tar package
 * `createStartScripts`: generates standard Java start scripts
 * `createInitScript`: generates daemonizing init.sh script
 * `createManifest`: generates a simple yaml file describing the package content
 * `run`: runs the specified `mainClass` with default `args`

License
-------
This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
