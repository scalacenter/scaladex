#!/bin/bash

source $(dirname $0)/base.sh

# get the latest data
pushd contrib
git checkout master
git pull origin master
popd

pushd index
git checkout master
git pull origin master
popd

export JVM_OPTS="-Xms1G -Xmx3G -XX:ReservedCodeCacheSize=256m -XX:+TieredCompilation -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC"
./sbt "data/run all"

# publish the latest data
pushd contrib
git add -A
git commit -m "`date`"
git push origin master
popd

pushd index
git add -A
git commit -m "`date`"
git push origin master
popd