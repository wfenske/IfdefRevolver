#!/usr/bin/env sh

me_dir=$(dirname "$0")
. "${me_dir}"/setup_classpath.sh || exit $?

exec java -cp "${CP:?}" de.ovgu.skunk.commitanalysis.changedfunctions.main.Main "$@"
