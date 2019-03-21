#!/usr/bin/env sh

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")
. "${me_dir}"/setup_classpath.sh || exit $?

# Increase memory
o_jvm="-Xms2g -Xmx8g"

exec java ${o_jvm} -cp "${CP:?}" de.ovgu.ifdefrevolver.commitanalysis.ListAllFunctions "$@"
