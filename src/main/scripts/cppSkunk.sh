#!/usr/bin/env bash

#source=${2:?Temporary snapshot directory (second positional argument) is missing.}
source=${PWD:?PWD (current working directory) unset or empty.}
#config=${1:?"Smell detection configuration file (first positional argument) is missing."}
#resultsDir=${2:?"Results snapshot directory (second positional argument) is missing."}

# Optional additional cppstats options, such as, --lazyPreparation or --prepareFrom=../<previous-snapshot>/cppstats_input.txt
cppstatsLazyOpt=${1}

#SKUNK_COMMAND=skunk.sh

# For cppstats and src2srcml and srcml2src
PATH=$HOME/bin:$PATH

# (2) cppstats
echo "Starting cppstats in $PWD ..." >&2

general_opts='--nobak --kind general'
featurelocations_opts='--nobak --kind featurelocations'
if [ -n "$cppstatsLazyOpt" ]
then
    cppstats $general_opts "$cppstatsLazyOpt"          &
    cppstats_gen_pid=$!
    cppstats $featurelocations_opts "$cppstatsLazyOpt" &
    cppstats_loc_pid=$!
else
    
    cppstats $general_opts                             &
    cppstats_gen_pid=$!
    cppstats $featurelocations_opts                    &
    cppstats_loc_pid=$!
fi

wait $cppstats_gen_pid
err=$?
if [ $err -ne 0 ]
then
    echo "\`cppstats $general_opts $cppstatsLazyOpt' failed with exit code ${err}." >&2
    exit $err
fi

wait $cppstats_loc_pid
err=$?
if [ $err -ne 0 ]
then
    echo "\`cppstats $featurelocations_opts $cppstatsLazyOpt failed with exit code ${err}." >&2
    exit $err
fi

echo "cppstats finished." >&2
  
# (2.5) skunk
#echo "Starting SKUNK ..." >&2

#mkdir -p -- "$resultsDir" || exit $?
#cd -- "$resultsDir" || exit $?
#$SKUNK_COMMAND --source="$source" --save-intermediate --config="$config"
#err=$?
#if [ $err -ne 0 ]
#then
#    echo "\`$SKUNK_COMMAND --source=$source --save-intermediate --config=$config' failed with exit code ${err}." >&2
#    exit $err
#fi
#echo "SKUNK finished" >&2
