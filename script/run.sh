#!/usr/bin/env bash

set -x #echo on

HERE="`dirname $0`"

# install ammonite repl
if [ ! -f $HERE/amm ];
then
  curl -L https://git.io/vKSOR > $HERE/amm
  chmod a+x $HERE/amm
fi

./$HERE/amm $HERE/Run.sc "$@"
