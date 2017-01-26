#!/bin/bash

config=${1:?"Smell detection configuration file (first positional argument) is missing."}

#echo "$0 $@ in $PWD" >&2; exit 1

SKUNK_COMMAND=$(dirname "$0")/../../Skunk/VARISCAN/skunk.sh

# (2.5) skunk
echo "Starting SKUNK --processed ..." >&2

$SKUNK_COMMAND --processed="$PWD" --config="$config"
err=$?
if [ $err -ne 0 ]
then
    echo "\`$SKUNK_COMMAND --processed=$PWD --config=$config' failed with exit code ${err}." >&2
    exit $err
fi
echo "SKUNK --processed finished successfully" >&2
