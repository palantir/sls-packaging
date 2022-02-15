<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/sls-packaging"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# SLS Distribution Gradle Plugins

[![Build Status](https://circleci.com/gh/palantir/sls-packaging.svg?style=shield)](https://circleci.com/gh/palantir/sls-packaging)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/com/palantir/sls-java-service-distribution/com.palantir.sls-java-service-distribution.gradle.plugin/maven-metadata.xml.svg?label=plugin&logo=gradle)](https://plugins.gradle.org/plugin/com.palantir.sls-java-service-distribution)

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
                linux-amd64/go-java-launcher  # Native Java launcher binary (Linux x86_64)
                linux-arm64/go-java-launcher  # Native Java launcher binary (Linux arm64)
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
See [below](#java-service-distribution-plugin) for usage.

## Asset Distribution Gradle Plugin

This plugin helps package static files and directories into a distribution that conforms with Palantir's SLS asset
layout conventions.  Asset distributions differ from service distributions in that they do not have a top-level
`service` or `var` directory, and instead utilize a top-level `asset` directory that can contain arbitrary files.
See [below](#asset-distribution-plugin) for usage.

## Usage

### Product dependencies

'Product dependencies' are declarative metadata about the products your product/asset requires in order to function. When you run `./gradlew distTar`, your product dependencies are embedded in the resultant dist in the `deployment/manifest.yml` file.

Most of your product dependencies should be inferred automatically from on the libraries you depend on.  Any one of these jars may contain an embedded 'recommended product dependency' in its MANIFEST.MF (embedded using the [Recommended Product Dependencies Plugin][]).

However, you can also use the `productDependency` block to specify these manually (although this is no longer considered a best-practise). Please note: you can add further restrictions to existing constraints, but you can't broaden them:

```gradle
distribution {
    productDependency {
        productGroup = "com.palantir.group"
        productName = "my-service"
        minimumVersion = "1.0.0"
        maximumVersion = "1.x.x"
        recommendedVersion = "1.2.1"
        optional = false
    }
}
```

sls-packaging also maintains a lockfile, `product-dependencies.lock`, which should be checked in to Git.  This file is an accurate reflection of all the inferred and explicitly defined product dependencies. Run **`./gradlew --write-locks`** or **`./gradlew writeProductDependenciesLocks`** to update it. e.g.

```
# Run ./gradlew --write-locks to regenerate this file
com.palantir.auth:auth-service (1.2.0, 1.6.x)
com.palantir.storage:storage-service (3.56.0, 3.x.x)
com.palantir.email:email-service (1.200.3, 2.x.x) optional
com.palantir.foo:foo-service ($projectVersion, 1.x.x)
```

_The `$projectVersion` string is a placeholder that will appear if your repo publishes multiple services, and one of them depends on another.  The actual manifest will contain a concrete version._

The suffix `optional` will be added for `optional = true` in the `productDependency` declaration. All dependencies are required by default. 

It's possible to further restrict the acceptable version range for a dependency by declaring a tighter constraint in a
`productDependency` block - this will be merged with any constraints detected from other jars.
If all the constraints on a given product don't overlap, then an error will the thrown:
`Could not merge recommended product dependencies as their version ranges do not overlap`.

It's also possible to explicitly ignore a dependency or mark it as optional if it comes as a recommendation from a jar:

```gradle
distribution {
    productDependency {
        // ...
    }
    ignoredProductDependency('other-group3', 'other-service3')
    optionalProductDependency('other-group4', 'other-service4')
}
```
Dependencies marked as optional will appear with the `optional` suffix in the lockfile.

#### Accessing product dependencies

You can programmatically access the minimum product dependency version as follows:
```gradle
def myDependency = getMinimumProductVersion('com.palantir.service:my-service')
```

More often though, you probably just want to get the minimum product dependencies as a gradle configuration
that you can depend on from other projects. For this purpose, there is a configuration called `productDependencies`
that is published from each SLS project.

You can then use this together with [gradle-docker](https://github.com/palantir/gradle-docker/#specifying-and-publishing-dependencies-on-docker-images)
to inject your product dependencies into the docker-compose templating, for instance.

For example, given a dist project, `:my-service`, you can collect wire up docker :

```gradle
// from another project
apply plugin: 'com.palantir.docker'
dependencies {
    docker project(path: ':my-service', configuration: 'productDependencies')
}
```

## Packaging plugins

_These plugins require at least Gradle 4.10._

### Java Service Distribution plugin

Apply the plugin using standard Gradle convention:

    plugins {
        id 'com.palantir.sls-java-service-distribution'
    }

Additionally, declare the version of [go-java-launcher](https://github.com/palantir/go-java-launcher) to use:

```
# Add to 'versions.props' 
com.palantir.launching:* = 1.18.0
```

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
            maximumVersion = "1.5.x"      // optional, defaults to "1.x.x" (same major version as minimumVersion)
            recommendedVersion = "1.3.0"  // optional
        }
    }

And the complete list of configurable properties:

 * (optional) `serviceName` the name of this service, used to construct the final artifact's file name.
   Defaults to the configured "name" of the Gradle project, `project.name`.
 * (optional) `serviceGroup` the group of the service, used in the final artifact's manifest.
   Defaults to the configured "group" of the Gradle project, `project.group`.
 * (optional) `manifestExtensions` a map of extended manifest attributes, as specified in SLS 1.0
 * (optional) `productDependency` adds an entry to the `extensions.product-dependencies` block of the SLS manifest,
   declaring that this service has a dependency on the given other service with specific version bounds. The `productDependency` object must specify the following properties:
   * `productGroup` the `serviceGroup` of the dependency.
   * `productName` the `serviceName` of the dependency.
   * `minVersion` the minimal compatible version of the dependency.
   * `maxVersion` the maximal compatible version of the dependency.
   * `recommended` the version developers think you should use; most commonly the version of the implementation that was tested during CI (`minVersion` typically matches the version of the api you use to negotiate).
 * (optional) `mainClass` class containing the entry point to start the program. Defaults to this sole class containing a main method in the main source set if one exists.
 * (optional) `args` a list of arguments to supply when running `start`.
 * (optional) `checkArgs` a list of arguments to supply to the monitoring script, if omitted,
   no monitoring script will be generated.
 * (optional) `env` a map of environment variables that will be placed into the `env` block
   of the static launcher config. See [go-java-launcher](https://github.com/palantir/go-java-launcher)
   for details on the custom environment block.
 * (optional) `defaultJvmOpts` a list of default JVM options to set on the program.
 * (optional) `enableManifestClasspath` a boolean flag; if set to true, then the explicit Java
   classpath is omitted from the generated start scripts and static launcher config and instead
   inferred from a JAR file whose MANIFEST contains the classpath entries.
 * (optional) `excludeFromVar` a list of directories (relative to `${projectDir}/var`) to exclude from the distribution,
   defaulting to `['log', 'run']`.
 * (optional) `javaVersion` a fixed override for the desired major Java runtime version (e.g. `javaVersion JavaVersion.VERSION_15`).
   This defaults to the Java `targetCompatibility` version.
   Setting this automatically sets `javaHome` to the appropriate corresponding value.
 * (optional) `javaHome` a fixed override for the `JAVA_HOME` environment variable that will
   be applied when `init.sh` is run. When your `targetCompatibility` is Java 8 or less, this value will be blank. For
   Java 9 or higher will default to `$JAVA_<majorversion>_HOME` ie for Java 11 this would be `$JAVA_11_HOME`.
 * (optional) `gc` override the default GC settings. Available GC settings: `throughput` (default for Java 14 and lower), `hybrid` (default for Java 15 and higher) and `response-time`. Additionally, there is also `dangerous-no-profile` which does not apply any additional JVM flags and allows you to fully configure any GC settings through JVM options (not recommended for normal usage!). 
 * (optional) `addJava8GcLogging` add java 8 specific gc logging options.

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

#### Directories created at runtime
The plugin configures [go-java-launcher](https://github.com/palantir/go-java-launcher) to create the following
directories before starting the service:

* var/data/tmp

Additionally, the following directories are created in every SLS distribution created:

* var/log
* var/run


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
 * (optional) `productDependency` adds an entry to the `extensions.product-dependencies` block of the SLS manifest,
   declaring that this asset has a dependency on the given other product with specific version bounds.
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

To create a compressed, gzipped tar file of the distribution, run the `distTar` task. To create a compressed,
gzipped tar file of the deployment metadata for the distribution, run the `configTar` task.

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
 * `configTar`: creates the gzipped tar package of the deployment configuration
 * `createManifest`: generates a simple yaml file describing the package content

Specific to the Java Service plugin:

 * `createStartScripts`: generates standard Java start scripts
 * `createInitScript`: generates daemonizing init.sh script
 * `run`: runs the specified `mainClass` with default `args`

### Recommended Product Dependencies Plugin
[Recommended Product Dependencies Plugin]: #recommended-product-dependencies-plugin

This plugin allows API jars to declare the recommended product dependencies an SLS service distribution should take.

An example application of this plugin might look as follows:


```gradle
apply plugin: 'java'
apply plugin: 'com.palantir.sls-recommended-dependencies'

recommendedProductDependencies {
    productDependency {
        productGroup = 'com.foo.bar.group'
        productName = 'product'
        minimumVersion = rootProject.version
        maximumVersion = "${rootProject.version.tokenize('.')[0].toInteger()}.x.x"
        recommendedVersion = rootProject.version
        optional = false
    }
}
```

The recommended product dependencies will be serialized into the jar manifest of the jar that the project produces. The SLS distribution and asset plugins will inspect the manifest of all jars in the server or asset and extract the recommended product dependencies.

## License

This plugin is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
