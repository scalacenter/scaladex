#!/usr/bin/env bash

set -x

HERE="`dirname $0`"

DEST=$HERE/../.git/hooks/pre-commit

if [ ! -f $DEST ]; then
  cp pre-commit $DEST
fi
