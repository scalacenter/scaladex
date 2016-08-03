set -x #echo on

# install ammonite repl
if [ ! -f amm ];
then
  curl -L https://git.io/vKSOR > amm
  chmod a+x amm
fi

printenv

./amm script/Run.sc $GIT_BRANCH "$@"