# install ammonite repl
if [ ! -f amm ];
then
  curl -L https://git.io/vKSOR > amm
  chmod a+x amm
fi

./amm script/Run.sc "$@"