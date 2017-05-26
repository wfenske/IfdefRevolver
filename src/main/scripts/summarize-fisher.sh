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

summarize_as_csv()
{
    csvsql --query "select
I
,case
	when D='LINES_CHANGED' then 'LCHG'
	when D='COMMITSratio'  then 'COMMITS/LOC'
	when D='HUNKSratio'    then 'HUNKS/LOC'
	when D='LCHratio'      then 'LCHG/LOC'
	else D
end D
,N001
,N005
,NINS
,MIN_COHEND,MEAN_COHEND,MAX_COHEND
,case
	when abs(min_cohend) < 0.2 then 'negligible'
	when abs(min_cohend) < 0.5 then 'small'
	when abs(min_cohend) < 0.8 then 'medium'
	else 'large'
end MIN_MAGNITUDE
,case
	when abs(mean_cohend) < 0.2 then 'negligible'
	when abs(mean_cohend) < 0.5 then 'small'
	when abs(mean_cohend) < 0.8 then 'medium'
	else 'large'
end MEAN_MAGNITUDE
,case
	when abs(max_cohend) < 0.2 then 'negligible'
	when abs(max_cohend) < 0.5 then 'small'
	when abs(max_cohend) < 0.8 then 'medium'
	else 'large'
end MAX_MAGNITUDE
from (select 
     	     D,I
	     ,min(CohenD) MIN_COHEND
	     ,avg(CohenD) MEAN_COHEND
	     ,max(CohenD) MAX_COHEND
	     ,sum(P001) N001
	     ,sum(P005) N005
	     ,sum(PINS) NINS
	     ,case
		when D LIKE '%ratio' then 1
		else 0
	     end RATIOP
     from
	(SELECT *
		,case when ChisqP < 0.01 then 1 else 0 end P001
		,case when ChisqP >= 0.01 and ChisqP < 0.05 then 1 else 0 end P005
		,case when ChisqP >= 0.05 then 1 else 0 end PINS
	FROM fisher) p
     group by D,I)
as agg order by ratiop,I,D" \
       --tables fisher "$1"
}

line_to_tex()
{
    cat|sed -e 's/,/ /g' -e 's,/LOC,\\\\textsubscript{ratio},g' |xargs printf '\\metric{%s} & \\metric{%s} & \$%d\$ & \$%d\$ & \$%d\$ & \$%.2f\$ & \$%.2f\$ & \$%.2f\$ & %s & %s & %s\\\\\n'
}

csv_table_to_tex()
{
    tail -n+2|while read line; do printf '%s\n' "$line"|line_to_tex; done
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
