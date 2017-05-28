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

group_min_abs_cohned()
{
    ##file="${1:?}"
    ##indep="${2:?}"
    ##dep="${3:?}"
    csvsql -d ',' -q '"' --query "select I,D,COHEND, case when abs(CohenD) < 0.2 then 'negligible' when abs(CohenD) < 0.5 then 'small' when abs(CohenD) < 0.8 then 'medium' else 'large' end MAGNITUDE from fisher where i='${2:indep}' and d='${3:?dep}' order by abs(CohenD) asc limit 1" --tables fisher "${1:?file}"|tail -n+2
}

group_max_abs_cohned()
{
    ##file="${1:?}"
    ##indep="${2:?}"
    ##dep="${3:?}"
    csvsql -d ',' -q '"' --query "select I,D,COHEND, case when abs(CohenD) < 0.2 then 'negligible' when abs(CohenD) < 0.5 then 'small' when abs(CohenD) < 0.8 then 'medium' else 'large' end MAGNITUDE from fisher where i='${2:indep}' and d='${3:?dep}' order by abs(CohenD) desc limit 1" --tables fisher "${1:?file}"|tail -n+2
}

summarize_as_csv()
{
    indeps=$(csvsql -d ',' -q '"' --query "select distinct i from fisher" --tables fisher "$1"|tail -n +2)
    deps=$(csvsql -d ',' -q '"' --query "select distinct d from fisher" --tables fisher "$1"|tail -n +2)
    min_out_tmp=$(mktemp -- fisher_min.XXXXXXXX) || exit $?
    max_out_tmp=$(mktemp -- fisher_max.XXXXXXXX) || exit $?
    echo "I,D,COHEND,MAGNITUDE" > "$min_out_tmp"
    echo "I,D,COHEND,MAGNITUDE" > "$max_out_tmp"
    log_info "Gathering minimum/maximum effect sizes ..."
    for i in $indeps
    do
	log_info "Gathering min/max effect sizes for $i ..."
	for d in $deps
	do
	    group_min_abs_cohned "$1" "$i" "$d" >> "$min_out_tmp"
	    group_max_abs_cohned "$1" "$i" "$d" >> "$max_out_tmp"
	done
    done

    log_info "Combining min/max effect sizes with the rest of the information."
    csvsql -d ',' -q '"' --query "select
agg.I
,agg.D
,agg.N001
,agg.N005
,agg.NINS
,mins.COHEND
,MEAN_COHEND
,maxs.COHEND
,mins.MAGNITUDE
,case
	when abs(agg.mean_cohend) < 0.2 then 'negligible'
	when abs(agg.mean_cohend) < 0.5 then 'small'
	when abs(agg.mean_cohend) < 0.8 then 'medium'
	else 'large'
end MEAN_MAGNITUDE
,maxs.MAGNITUDE
from (select 
     	     p.D,p.I
	     ,avg(p.CohenD) MEAN_COHEND
	     ,sum(p.P001) N001
	     ,sum(p.P005) N005
	     ,sum(p.PINS) NINS
	     ,case
		when D LIKE '%ratio' then 1
		else 0
	     end RATIOP
	     ,case
		when I = 'LOC' then 1
		else 0
	     end LOCP
     from
	(SELECT *
		,case when abs(FisherP) < 0.01 then 1 else 0 end P001
		,case when abs(FisherP) >= 0.01 and abs(FisherP) < 0.05 then 1 else 0 end P005
		,case when abs(FisherP) >= 0.05 then 1 else 0 end PINS
	FROM fisher) p
     group by D,I) as agg
JOIN mins ON mins.i=agg.i and mins.d=agg.d
JOIN maxs ON maxs.i=agg.i and maxs.d=agg.d
order by agg.ratiop,agg.locp,agg.I,agg.D" \
	   --tables fisher,mins,maxs "$1" "$min_out_tmp" "$max_out_tmp"
    rm -f -- "$min_out_tmp" "$max_out_tmp"
}

line_to_tex()
{
    printf "$line"|sed -e 's|,| |g' -e 's,ratio,\\\\textsubscript{/LOC},g' -e 's,LCH,LCHG,g' -e 's,LINES_CHANGED,LCHG,g'|xargs printf '\\metric{%s} & \\metric{%s} & \$%d\$ & \$%d\$ & \$%d\$ & \$%.2f\$ & \$%.2f\$ & \$%.2f\$ & \\e%s{} & \\e%s{} & \\e%s{}\\\\\n'
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
