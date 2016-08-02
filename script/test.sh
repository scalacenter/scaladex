#!/bin/bash

if [ "`git rev-parse --abbrev-ref HEAD`" -ne "master" ];
then

source $(dirname $0)/base.sh

export JVM_OPTS="-Xms1G -Xmx2G -XX:ReservedCodeCacheSize=256m -XX:+TieredCompilation -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC"
./sbt ";clean ;test"

fi