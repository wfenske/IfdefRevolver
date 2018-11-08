#!/usr/bin/env bash

source=${PWD:?PWD (current working directory) unset or empty.}

# Optional, additional cppstats options, such as, --lazyPreparation or --prepareFrom=../<previous-snapshot>/cppstats_input.txt
cppstatsLazyOpt=${1}

# For cppstats and src2srcml and srcml2src
PATH=$HOME/bin:$PATH

echo "Starting cppstats in $PWD ..." >&2

case "x$cppstatsLazyOpt" in
    x--prepareFrom=*)
	prepFromDir=${cppstatsLazyOpt#--prepareFrom=}
	prepFromDir=${prepFromDir%/cppstats_input.txt}
	prepare-from-previous-checkout.sh "$prepFromDir" "$source" || exit $?
	;;
esac

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
