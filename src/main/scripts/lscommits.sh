#!/usr/bin/env sh

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")
. "${me_dir}"/setup_classpath.sh || exit $?

exec java -cp "$CP" de.ovgu.ifdefrevolver.bugs.minecommits.main.ListCommits "$@"
