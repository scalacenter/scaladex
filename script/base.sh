#!/bin/bash

set -x #echo on

# install sbt
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

# set bintray credentials
git clone git@github.com:scalacenter/scaladex-credentials.git

if [ ! -d ~/.bintray ];
then
  mkdir ~/.bintray
fi

# to list poms
if [ ! -f ~/.bintray/.credentials2 ];
then
  cp scaladex-credentials/search-credentials ~/.bintray/.credentials2
fi

# to publish sbt plugin
if [ ! -f ~/.bintray/.credentials ];
then
  cp scaladex-credentials/sbt-plugin-credentials ~/.bintray/.credentials
fi

git submodule init
git submodule update