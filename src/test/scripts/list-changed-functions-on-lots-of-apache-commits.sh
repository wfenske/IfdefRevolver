#!/usr/bin/env sh

me_dir=$(dirname "$0")
cat $me_dir/lots-of-apache-commits.lst|xargs ${me_dir}/../../../src/main/scripts/lschfuncs.sh -r ${HOME}/src/skunk/trialrun/repos/apache
