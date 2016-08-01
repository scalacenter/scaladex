#!/bin/bash

source $(dirname $0)/base.sh

export JVM_OPTS="-Xms1G -Xmx2G -XX:ReservedCodeCacheSize=256m -XX:+TieredCompilation -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC"
./sbt "server/universal:packageBin"

cp server/target/universal/server-0.1.3.zip