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
o_log_level=$LOG_LEVEL_INFO
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
    echo " $me [-t] FISHER_CSV"
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
    echo "Summarize the data from the fisher.csv file."
    echo
    echo "Example:"
    echo " $me results/fisher-summary.csv"
    echo 
    echo "Options:"
    echo " -t Format the result as tex code for a table."
    echo " -h Print this help screen and exit."
    echo 
    echo "Exit code is 0 on success, non-zero otherwise."
}

group_cohned_averages()
{
    ##file="${1:?}"
    ##indep="${2:?}"
    ##dep="${3:?}"

    csvsql -d ',' -q '"' --query "select CLIFFD
from fisher
where i='${2:indep}' and d='${3:?dep}' and abs(MWUP) < 0.01" \
	   --tables fisher "${1:?file}"|mean-sd-se.R -c CliffD --digits 5 -H
}

summarize_as_csv()
{
    indeps=$(csvsql -d ',' -q '"' --query "select distinct i from fisher" --tables fisher "$1"|tail -n +2)
    deps=$(csvsql -d ',' -q '"' --query "select distinct d from fisher" --tables fisher "$1"|tail -n +2)
    cliffd_averages_tmp=$(mktemp -- fisher_cliffd_averages.XXXXXXXX) || exit $?

    ## The header produced by mean-sd-se.R looks like this:
    ##
    ## N,M(CliffD),SD(CliffD),SE(CliffD)
    ##
    ## This is not very handy in SQL, so we rename it a bit. We also
    ## add the independent and dependent variable names in front,
    ## making it look like this:
    ##
    ## I,D,N,M_D,SD_D,SE_D
    echo "I,D,N,M_D,SD_D,SE_D" > "$cliffd_averages_tmp"
    log_info "Gathering average effect sizes ..."
    for i in $indeps
    do
	log_info "Gathering average effect sizes for $i ..."
	for d in $deps
	do
	    printf '%s,%s,' "$i" "$d" >> "$cliffd_averages_tmp"
	    group_cohned_averages "$1" "$i" "$d" >> "$cliffd_averages_tmp"
	done
    done

    log_info "Combining average effect sizes with the rest of the information."
    csvsql -d ',' -q '"' --query "SELECT
agg.I
,agg.D
,agg.N001
,agg.NINS
,cliffs.m_d MEAN_D
,cliffs.sd_d SD_D
,case
	when abs(cliffs.dlow) < 0.147 then 'negligible'
	when abs(cliffs.dlow) < 0.33  then 'small'
	when abs(cliffs.dlow) < 0.474 then 'medium'
	else 'large'
end LOW_MAGNITUDE
,case
	when abs(cliffs.davg) < 0.147 then 'negligible'
	when abs(cliffs.davg) < 0.33  then 'small'
	when abs(cliffs.davg) < 0.474 then 'medium'
	else 'large'
end AVG_MAGNITUDE
,case
	when abs(cliffs.dhigh) < 0.147 then 'negligible'
	when abs(cliffs.dhigh) < 0.33  then 'small'
	when abs(cliffs.dhigh) < 0.474 then 'medium'
	else 'large'
end HIGH_MAGNITUDE
FROM (select 
     	     p.D,p.I
	     ,sum(p.P001) N001
	     ,sum(p.PINS) NINS
	     ,case
		when D LIKE '%ratio' then 1
		else 0
	     end RATIOP
	     ,case
		when I = 'LOC' then 1
		else 0
	     end LOCP
	     ,case
		when I = 'FL'  then 1
		when I = 'FC'  then 2
		when I = 'ND'  then 3
		when I = 'NEG' then 4
		else 5 /* should never happen */
	     end I_SORT_KEY
     from
	(SELECT *
		,case when abs(MWUP) < 0.01 then 1 else 0 end P001
		,case when abs(MWUP) >= 0.01 then 1 else 0 end PINS
	FROM fisher) p
     group by D,I) as agg
JOIN (select *,(m_d - sd_d) as dlow, m_d as davg, (m_d + sd_d) as dhigh
      from cliffs0) cliffs
     ON cliffs.i=agg.i and cliffs.d=agg.d
ORDER by agg.ratiop,agg.locp,agg.D,agg.I_SORT_KEY" \
	   --tables fisher,cliffs0 "$1" "$cliffd_averages_tmp"
    rm -f -- "$cliffd_averages_tmp"
}

line_to_tex()
{
    printf "$line"|sed -e 's|,| |g' \
		       -e 's,FL, fl_{>0},g' \
		       -e 's,FC, fc_{>1},g' \
		       -e 's,ND, nd_{>0},g' \
		       -e 's,NEG,neg_{>0},g' \
		       -e 's,LOC,loc^+,g' \
		       -e 's,COMMITSratio,\\\\fcommr,g' \
		       -e 's,LCHratio,\\\\flchgr,g' \
		       -e 's,COMMITS,\\\\fcomm,g' \
		       -e 's,LCH,\\\\flchg,g' \
	|xargs printf '$%s$ & %s & %d & (%d) & $%.2f$ & $%.2f$ & \\e%s{} & \\e%s{} & \\e%s{}\\\\\n'
}

csv_table_to_tex()
{
    tail -n+2|while read line; do line_to_tex "$line"; done
}

o_tex=false
while getopts "ht" o
do
    case "$o" in
	h) help
	   exit 0
	   ;;
	t) o_tex=true
	   ;;
	*) usage_and_die
	   ;;
    esac
done
shift $((OPTIND-1))

if [ $# -ne 1 ]
then
    log_error "Expected exactly one positional argument, got $#." >&2
    usage_and_die
fi

summary_file=$1

if [ ! -e "$summary_file" ]
then
    edie "No such file or directory: $summary_file"
fi

if [ -d "$summary_file" ]
then
    edie "Cannot summarize $summary_file: Is a directory."
fi

if [ ! -r "$summary_file" ]
then
    edie "Cannot read $summary_file"
fi

if $o_tex
then
    LC_NUMERIC=C; export LC_NUMERIC
    summarize_as_csv "$summary_file"|csv_table_to_tex
else
    summarize_as_csv "$summary_file"
fi
