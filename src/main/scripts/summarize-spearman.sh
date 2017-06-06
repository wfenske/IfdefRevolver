#!/usr/bin/env bash

me=$(basename -- "$0") || exit $?
real_me=$(realpath -- "$0") || exit $?
real_me_dir=$(dirname -- "$real_me") || exit $?

LOG_LEVEL_SILENT=0
LOG_LEVEL_ERROR=1
LOG_LEVEL_WARN=2
LOG_LEVEL_INFO=3
LOG_LEVEL_DEBUG=4

o_log_level=$LOG_LEVEL_WARN
#o_log_level=$LOG_LEVEL_INFO
#o_log_level=$LOG_LEVEL_DEBUG

##set -x

echo_as_me()
{
    echo "$me: $@"
}

log_debug()
{
    [ $o_log_level -ge $LOG_LEVEL_DEBUG ] && echo_as_me "DEBUG" "$@" >&2
}

log_info()
{
    [ $o_log_level -ge $LOG_LEVEL_INFO ] && echo_as_me "INFO" "$@" >&2
}

log_warn()
{
    [ $o_log_level -ge $LOG_LEVEL_WARN ] && echo_as_me "WARN" "$@" >&2
}

log_error()
{
    [ $o_log_level -ge $LOG_LEVEL_ERROR ] && echo_as_me "ERROR" "$@" >&2
}

edie()
{
    log_error "$@"
    exit 1
}

usage()
{
    echo "Usage:"
    echo " $me [-qv] [SPEARMN_CSV ...]"
    echo "Get help by executing:"
    echo " $me -h"
}

usage_and_die()
{
    usage >&2
    exit 1
}

help()
{
    usage
    echo 
    echo "Summarize the data in the spearman.csv files of individual subject systems. If no files are given, input is read from stdin."
    echo
    echo "Example:"
    echo " $me results/apache/spearman.csv results/openldap/spearman.csv"
    echo 
    echo "Options:"
    echo " -v Print more log messages."
    echo " -q Print less log messages."
    echo " -h Print this help screen and exit."
    echo 
    echo "Exit code is 0 on success, non-zero otherwise."
}

group_rho_averages()
{
    ##file=${1:?}
    ##indep="${2:?}"
    ##dep="${3:?}"
    csvsql -d ',' -q '"' --query "select rho
from spearman
where i='${2:indep}' and d='${3:?dep}'" \
	   --tables spearman "${1:?file}"|averages.R -c rho --digits 2 --min=absmin --func=median --max=absmax -H
}

summarize_as_csv()
{
    log_debug "Input file to summarize: $1"
    [ $o_log_level -ge $LOG_LEVEL_DEBUG ] && cat "$1"
    
    indeps=$(csvsql -d ',' -q '"' --query "select distinct i from spearman" --tables spearman "$1")
    if [ $? -ne 0 ]
    then
	log_error "Failed to select independent variables."
	return 1
    fi
    indeps=$(printf '%s\n' "$indeps"|tail -n +2)
    log_debug "Selected indeps: $indeps"
    
    deps=$(csvsql -d ',' -q '"' --query "select distinct d from spearman" --tables spearman "$1")
    if [ $? -ne 0 ]
    then
	log_error "Failed to select dependent variables."
	return 1
    fi
    deps=$(printf '%s\n' "$deps"|tail -n +2)
    log_debug "Selected deps: $deps"
    
    averages_tmp=$(mktemp -- spearman_averages.XXXXXXXX)
    if [ $? -ne 0 ]
    then
	log_error "Failed to create temporary file."
	return 1
    fi
    
    echo "I,D,RMIN,RAVG,RMAX" > "$averages_tmp"
    log_info "Gathering minimum/average/maximum effect sizes ..."
    
    for i in $indeps
    do
	log_info "Gathering min/avg/max effect sizes for $i ..."
	for d in $deps
	do
	    printf '%s,%s,' "$i" "$d" >> "$averages_tmp"
	    group_rho_averages "$1" "$i" "$d" >> "$averages_tmp"
	    if [ $? -ne 0 ]
	    then
		log_error "Failed to create summaries for $i, $d."
		rm -f -- "$averages_tmp"
		return 1
	    fi
	done
    done

    log_debug "Temporary averages of stacked data stored in $averages_tmp:"
    [ $o_log_level -ge $LOG_LEVEL_DEBUG ] && cat "$averages_tmp"

    log_info "Combining min/max effect sizes with the rest of the information."
    csvsql -d ',' -q '"' --query "select
spearman.I
,spearman.D
,count(*) as NUM_SYSTEMS
,averages.rmin
,averages.ravg
,averages.rmax
,case
	when abs(averages.rmin) < 0.2 then 'very weak'
	when abs(averages.rmin) < 0.4 then 'weak'
	when abs(averages.rmin) < 0.6 then 'moderate'
	when abs(averages.rmin) < 0.8 then 'strong'
	else 'very strong'
end MIN_MAGNITUDE
,case
	when abs(averages.ravg) < 0.2 then 'very weak'
	when abs(averages.ravg) < 0.4 then 'weak'
	when abs(averages.ravg) < 0.6 then 'moderate'
	when abs(averages.ravg) < 0.8 then 'strong'
	else 'very strong'
end AVG_MAGNITUDE
,case
	when abs(averages.rmax) < 0.2 then 'very weak'
	when abs(averages.rmax) < 0.4 then 'weak'
	when abs(averages.rmax) < 0.6 then 'moderate'
	when abs(averages.rmax) < 0.8 then 'strong'
	else 'very strong'
end MAX_MAGNITUDE
FROM spearman
JOIN averages ON spearman.i=averages.i and spearman.d=averages.d
GROUP BY spearman.i,spearman.d
ORDER BY spearman.i,spearman.d" \
	   --tables spearman,averages "$1" "$averages_tmp"
    err=$?
    rm -f -- "$averages_tmp"
    return $err
}

while getopts "hvq" o
do
    case "$o" in
	h) help
	   exit 0
	   ;;
	q) o_log_level=$(( $o_log_level - 1 ))
	   ;;
	v) o_log_level=$(( $o_log_level + 1 ))
	   ;;
	*) usage_and_die
	   ;;
    esac
done
shift $((OPTIND-1))

LC_NUMERIC=C; export LC_NUMERIC
stacked_input=$(mktemp -- stacked_spearman_input.XXXXXXXX) || edie "Failed to create temporary file."
if [ $# -eq 0 ]
then
    cat > "$stacked_input"
elif [ $# -eq 1 ]
then
    cat -- "$1" > "$stacked_input"
else
    csvstack -d ',' -q '"' "$@" > "$stacked_input"
fi
err=$?

log_debug "Temporary stacked data stored in ${stacked_input}:"
[ $o_log_level -ge $LOG_LEVEL_DEBUG ] && cat "$stacked_input"

if [ $err -eq 0 ]
then
    summarize_as_csv "$stacked_input"
    err=$?
fi
rm -f "$stacked_input"
exit $err
