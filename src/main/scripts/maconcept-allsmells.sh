#!/usr/bin/env sh
dir_me=$(dirname -- "$0")
maconcept=$dir_me/maconcept.sh

$maconcept "$@" -s AB --source &&
$maconcept "$@" -s AF --processed &&
$maconcept "$@" -s LF --processed
