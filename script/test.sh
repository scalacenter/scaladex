#!/bin/bash

source $(dirname $0)/base.sh

export JVM_OPTS="-Xms1G -Xmx2G -XX:ReservedCodeCacheSize=256m -XX:+TieredCompilation -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC"
./sbt ";clean ;test"