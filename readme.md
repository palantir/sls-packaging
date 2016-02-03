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
            manifest.yml             # simple package manifest
        service/
            bin/
                [service-name]       # start script
                [service-name.bat]   # Windows start script
                init.sh              # daemonizing script
                config.sh            # customized environment vars
            lib/
                [jars]
        var/
            # application configuration and data

Packages are produced as gzipped tar names `[service-name]-[project-version].tgz`.

Usage
-----
Apply the plugin using standard gradle convention:

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
 * (optional) `defaultJvmOpts` a list of default JVM options to set on the program.
 * (optional) `enableManifestClasspath` a boolean flag; if set to true, then the explicit Java
   classpath is omitted from the generated Windows start script and instead infered
   from a JAR file whose MANIFEST contains the classpath entries.
 * (optional) `javaHome` a fixed override for the `JAVA_HOME` environment variable that will
   be applied when `init.sh` is run.


Packaging
---------
To create a compressed, gzipped tar file, run the `distTar` task.

As part of package creation, this plugin will create three shell scripts:

 * `service/bin/[service-name]`: a Gradle default start script for running
   the defined `mainClass`
 * `service/bin/init.sh`: a shell script to assist with daemonizing a JVM
   process. The script takes a single argument of `start`, `stop`, `console` or `status`.
   - `start`: On calls to `service/bin/init.sh start`,
     `service/bin/[serviceName] [args]` will be executed, disowned, and a pid file
     recorded in `var/run/[service-name].pid`.
   - `console`: like `start`, but does not background the process.
   - `status`: returns 0 when `var/run/[service-name].pid` exists and a
     process the id recorded in that file with a command matching the expected
     start command is found in the process table.
   - `stop`: if the process status is 0, issues a kill signal to the process.
 * `service/bin/config.sh`: a shell script containing environment variables to apply
    as overrides when `init.sh` is run.


In addition to creating these scripts, this plugin will merge the entire
contents of `${projectDir}/service` and `${projectDir}/var` into the package.

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
