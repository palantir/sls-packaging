#!/bin/bash
# Runs @mainClass@ in place of the go-java-launcher/go-init binaries.
# Paths are relative to the distribution root, which init.sh changes into before invoking this script.
exec "@javaHome@/bin/java" -cp "@classpath@" @mainClass@ "$@"
