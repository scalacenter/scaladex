#!/bin/bash

set -x #echo on

if [ ! -f sbt ];
then
  curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > sbt
  chmod a+x sbt
fi

# set scaladex github user private key in Jenkin
if [ -d scaladex-credentials ];
then
  rm -rf scaladex-credentials
fi

git clone git@github.com:scalacenter/scaladex-credentials.git

if [ ! -d ~/.bintray ];
then
  mkdir ~/.bintray
fi

if [ ! -f ~/.bintray/.credentials2 ];
then
  cp scaladex-credentials/search-credentials ~/.bintray/.credentials2
fi

if [ ! -f ~/.bintray/.credentials ];
then
  cp scaladex-credentials/sbt-plugin-credentials ~/.bintray/.credentials
fi

git submodule init
git submodule update

pushd contrib
git checkout master
git pull origin master
popd

pushd index
git checkout master
git pull origin master
popd

export JVM_OPTS="-Xms1G -Xmx3G -XX:ReservedCodeCacheSize=256m -XX:+TieredCompilation -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC"
./sbt -no-colors ";test ;data/run all"

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