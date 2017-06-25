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
    echo " $me REGRESSION_CSV"
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
    echo "Summarize the data from various regression models."
    echo
    echo "Example:"
    echo " $me results/regression-full-all.csv"
    echo 
    echo "Options:"
    echo " -h Print this help screen and exit."
    echo " -v Print more log messages."
    echo " -q Print less log messages."
    echo 
    echo "Exit code is 0 on success, non-zero otherwise."
}

summarize_as_csv()
{
    formulae=$(csvsql -d ',' -q '"' --query "select distinct trim(formula) from r" --tables r "$1"|tail -n +2)
    deps=$(csvsql -d ',' -q '"' --query "select distinct trim(d) from r" --tables r "$1"|tail -n +2)
    ##averages_tmp=$(mktemp -- regression_averages.XXXXXXXX) || exit $?

    log_info "Gathering average coefficients sizes ..."

    header=''
    for formula in $formulae
    do
	for dep in $deps
	do
	    log_info "$dep ~ $formula"
	    log_debug "Gathering indeps for $formula and dependent $dep ..."
	    indeps=$(csvsql -d ',' -q '"' --query "
select i from (
       select i
       	      , case
		    when(i)='(Intercept)' then 0
		    when(i)='FL' then 1
		    when(i)='FC' then 2
		    when(i)='ND' then 3
		    when(i)='NEG' then 4
		    when(i)='LOACratio' then 5
		    when(i)='LOC' then 6
		    else 7
	        end as sortkey
       from
       (select distinct trim(i) as i from r
        where trim(formula) = '${formula:?}' and trim(d) = '$dep'))
order by sortkey
" --tables r "$1"|tail -n +2)
	    debug_indeps=$(printf '%s\n' "$indeps"|tr '\n' ',')
	    log_debug "Indeps are: $debug_indeps"
	    for indep in $indeps
	    do
		csvsql -d ',' -q '"' --query "select COEF
from r
where trim(i)='${indep:?}' and trim(d)='${dep:?}' and trim(formula)='${formula:?}' and p<0.01" \
		       --tables r "${1:?file}"|mean-sd-se.R -c COEF --digits 3 $header -i "${indep},${dep},${formula}"|sed -e 's/(COEF)/_COEF/g' -e 's/Identifier/I,D,FORMULA/'
		header=-H
	    ##printf '%s,%s,' "$i" "$d" >> "$averages_tmp"
		##group_cohned_averages "$1" "$i" "$d" >> "$averages_tmp"
	    done
	done
    done
    ##rm -f -- "$averages_tmp"
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

summarize_as_csv "$summary_file"
