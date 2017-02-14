# SLS Distribution Gradle Plugins

[![Build Status](https://circleci.com/gh/palantir/sls-packaging.svg?style=shield)](https://circleci.com/gh/palantir/sls-packaging)
[![Download](https://api.bintray.com/packages/palantir/releases/sls-packaging/images/download.svg) ](https://bintray.com/palantir/releases/sls-packaging/_latestVersion)

A set of Gradle plugins that facilitate packaging projects for distributions conforming to Palantir's Service Layout
Specification. This project was formerly known as gradle-java-distribution.

The Java Service and Asset plugins cannot both be applied to the same gradle project, and
distributions from both are produced as a gzipped tar named `[service-name]-[project-version].sls.tgz`.

## Java Service Distribution Gradle Plugin

Similar to the standard application plugin, this plugin helps package Java
Gradle projects for easy distribution and execution. This distribution conforms with Palantir's 
SLS service layout conventions that attempt to split immutable files from mutable state and configuration.

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

## Asset Distribution Gradle Plugin

This plugin helps package static files and directories into a distribution that conforms with Palantir's SLS asset
layout conventions.  Asset distributions differ from service distributions in that they do not have a top-level
`service` or `var` directory, and instead utilize a top-level `asset` directory that can contain arbitrary files.

## Usage

### Java Service Distribution plugin

Apply the plugin using standard Gradle convention:

    plugins {
        id 'com.palantir.sls-java-service-distribution'
    }

A sample configuration for the Service plugin:

    distribution {
        serviceName 'my-service'
        serviceGroup 'my.service.group'
        mainClass 'com.palantir.foo.bar.MyServiceMainClass'
        args 'server', 'var/conf/my-service.yml'
        env 'KEY1': 'value1', 'KEY2': 'value1'
        manifestExtensions 'KEY3': 'value2'
        productDependency {
            productGroup = "other-group"
            productName = "other-service"
            minimumVersion = "1.1.0"
            maximumVersion = "1.3.x"
            recommendedVersion = "1.5.0"
        }
    }

And the complete list of configurable properties:

 * `serviceName` the name of this service, used to construct the final artifact's file name.
 * (optional) `serviceGroup` the group of the service, used in the final artifact's manifest.
   Defaults to the configured "group" of the Gradle project, `project.group`.
 * (optional) `manifestExtensions` a map of extended manifest attributes, as specified in SLS 1.0
 * (optional) `serviceDependency` adds an entry to the `extensions.service-dependencies` block of the SLS manifest,
   declaring that this service has a dependency on the given other service with specific version bounds.
 * `mainClass` class containing the entry point to start the program.
 * (optional) `args` a list of arguments to supply when running `start`.
 * (optional) `checkArgs` a list of arguments to supply to the monitoring script, if omitted,
   no monitoring script will be generated.
 * (optional) `env` a map of environment variables that will be placed into the `env` block
   of the static launcher config. See [go-java-launcher](https://github.com/palantir/go-java-launcher)
   for details on the custom environment block.
 * (optional) `defaultJvmOpts` a list of default JVM options to set on the program.
 * (optional) `enableManifestClasspath` a boolean flag; if set to true, then the explicit Java
   classpath is omitted from the generated Windows start script and instead inferred
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

### Asset Distribution plugin

Apply the plugin using standard Gradle convention:

    plugins {
        id 'com.palantir.sls-asset-distribution'
    }

A sample configuration for the Asset plugin:

    distribution {
        serviceName 'my-assets'
        assets 'relative/path/to/assets', 'relocated/path/in/dist'
        assets 'another/path, 'another/relocated/path'
    }

The complete list of configurable properties:

 * `serviceName` the name of this service, used to construct the final artifact's file name.
 * (optional) `serviceGroup` the group of the service, used in the final artifact's manifest.
   Defaults to the configured "group" of the Gradle project, `project.group`.
 * (optional) `manifestExtensions` a map of extended manifest attributes, as specified in SLS 1.0.
 * (optional) `serviceDependency` adds an entry to the `extensions.service-dependencies` block of the SLS manifest,
   declaring that this service has a dependency on the given other service with specific version bounds.
 * (optional) `assets <fromPath>` adds the specified file or directory (recursively) to the asset distribution,
   preserving the directory structure. For example, `assets 'foo/bar'` yields files `foo/bar/baz/1.txt` and `foo/bar/2.txt` in the
   asset distribution, assuming that the directory `foo/bar` contains files `baz/1.txt` and `2.txt`.
 * (optional) `assets <fromPath> <toPath>` as above, but adds the specified files relative to `toPath` in the asset distribution.
   For example, `assets 'foo/bar' 'baz'` yields files `baz/baz/1.txt` and `baz/2.txt` assuming that the directory `foo/bar` contains
   the files `baz/1.txt` and `2.txt`.
 * (optional) `setAssets <map<fromPath, toPath>>` as above, but removes all prior configured assets.

The example above, when applied to a project rooted at `~/project`, would create a distribution with the following structure:

    [service-name]-[service-version]/
        deployment/
            manifest.yml                      # simple package manifest
        asset/
            relocated/path/in/dist            # contents from `~/project/relative/path/to/assets/`
            another/relocated/path            # contents from `~/project/another/path`

Note that repeated calls to `assets` are processed in-order, and as such, it is possible to overwrite resources
by specifying that a later invocation be relocated to a previously used destination's ancestor directory.

### Packaging

To create a compressed, gzipped tar file, run the `distTar` task.

The plugins expose the tar file as an artifact in the `sls` configuration, making it easy to
share the artifact between sibling Gradle projects. For example:

```groovy
configurations { tarballs }

dependencies {
    tarballs project(path: ':other-project', configuration: 'sls')
}
```

As part of package creation, the Java Service plugin will additionally create three shell scripts:

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


Furthermore, the Java Service plugin will merge the entire contents of
`${projectDir}/service` and `${projectDir}/var` into the package.

### Tasks

 * `distTar`: creates the gzipped tar package
 * `createManifest`: generates a simple yaml file describing the package content

Specific to the Java Service plugin:

 * `createStartScripts`: generates standard Java start scripts
 * `createInitScript`: generates daemonizing init.sh script
 * `run`: runs the specified `mainClass` with default `args`

## License

This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
