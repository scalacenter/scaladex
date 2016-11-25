#!/usr/bin/env bash

set -x #echo on

HERE="`dirname $0`"

# install ammonite repl
if [ ! -f amm ];
then
  curl -L https://git.io/vKSOR > amm
  chmod a+x amm
fi

./amm $HERE/Run.sc "$@"
