#!/usr/bin/env sh

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")
. "${me_dir}"/setup_classpath.sh || exit $?

#( IFS=:; for e in $CP; do logcfg=${e}/log4j.xml; test -e "$logcfg" && echo $logcfg; done )

exec java -cp "${CP:?}" de.ovgu.skunk.commitanalysis.ListChangedFunctions "$@"
