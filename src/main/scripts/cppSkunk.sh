#!/usr/bin/env bash

#source=${2:?Temporary snapshot directory (second positional argument) is missing.}
source=${PWD:?PWD (current working directory) unset or empty.}
config=${1:?"Smell detection configuration file (first positional argument) is missing."}
resultsDir=${2:?"Results snapshot directory (second positional argument) is missing."}

# Optional additional cppstats options, such as, --lazyPreparation or --prepareFrom=../<previous-snapshot>/cppstats_input.txt
cppstatsLazyOpt=${3}

SKUNK_COMMAND=skunk.sh

# For cppstats an src2srcml and srcml2src
PATH=$HOME/bin:$PATH

# (2) cppstats
echo "Starting cppstats in $PWD ..." >&2

cppstats "$cppstatsLazyOpt" --nobak --kind general &
cppstats_gen_pid=$!
cppstats "$cppstatsLazyOpt" --nobak --kind featurelocations &
cppstats_loc_pid=$!

wait $cppstats_gen_pid
err=$?
if [ $err -ne 0 ]
then
    echo "\`cppstats --kind general' failed with exit code ${err}." >&2
    exit $err
fi

wait $cppstats_loc_pid
err=$?
if [ $err -ne 0 ]
then
    echo "\`cppstats --kind featurelocations' failed with exit code ${err}." >&2
    exit $err
fi

echo "CPPSTATS finished" >&2
  
# (2.5) skunk
echo "Starting SKUNK ..." >&2

mkdir -p -- "$resultsDir" || exit $?
cd -- "$resultsDir" || exit $?
$SKUNK_COMMAND --source="$source" --save-intermediate --config="$config"
err=$?
if [ $err -ne 0 ]
then
    echo "\`$SKUNK_COMMAND --source=$source --save-intermediate --config=$config' failed with exit code ${err}." >&2
    exit $err
fi
echo "SKUNK finished" >&2
