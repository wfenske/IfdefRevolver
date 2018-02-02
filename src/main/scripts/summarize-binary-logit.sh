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
    true
}

log_info()
{
    [ $o_log_level -ge $LOG_LEVEL_INFO ] && echo_as_me "INFO" "$@" >&2
    true
}

log_warn()
{
    [ $o_log_level -ge $LOG_LEVEL_WARN ] && echo_as_me "WARN" "$@" >&2
    true
}

log_error()
{
    [ $o_log_level -ge $LOG_LEVEL_ERROR ] && echo_as_me "ERROR" "$@" >&2
    true
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
    formulae=$(csvsql -d ',' -q '"' --query "select distinct formula from r" --tables r "$1"|tail -n +2)
    test $? -ne 0 && return 1
    log_debug "formulae: $formulae"
    deps=$(csvsql -d ',' -q '"' --query "select distinct d from r" --tables r "$1"|tail -n +2)
    test $? -ne 0 && return 1
    log_debug "deps: $deps"
    averag_coefs_tmp=$(mktemp -- regression_avg_coefs.XXXXXXXX) || exit $?
    averag_ors_tmp=$(mktemp -- regression_avg_ors.XXXXXXXX) || exit $?
    ###averag_mcfaddens_tmp=$(mktemp -- regression_avg_mcfadden.XXXXXXXX) || exit $?

    log_info "Gathering average coefficients sizes ..."

    skip_header='+1'
    err=0
    for dep in $deps
    do
	for formula in $formulae
	do
	    log_info "$dep ~ $formula"
	    log_debug "Gathering indeps for $formula and dependent $dep ..."
	    indeps=$(csvsql -d ',' -q '"' --query "
select i from (
       select i
       	      , case
		    when(i)='(Intercept)' then  0
		    when(i)='log2LOC'     then  1
		    when(i)='FL'          then  2
		    when(i)='FC'          then  3
		    when(i)='ND'          then  4
		    when(i)='NEG'         then  5
		    when(i)='LOACratio'   then  6
		    else                       42
	        end as sortkey
       from
       (select distinct i from r
        where formula = '${formula:?}' and d = '$dep'))
order by sortkey
" --tables r "$1")
	    err=$?; test $err -ne 0 && break
	    indeps=$(printf '%s\n' "$indeps"|tail -n +2)
	    debug_indeps=$(printf '%s\n' "$indeps"|tr '\n' ',')
	    log_debug "Indeps are: $debug_indeps"
	    for indep in $indeps
	    do
		coefs_ors=$(csvsql -d ',' -q '"' --query "
select COEF, \`OR\` as ODDSRATIO
from r
where
        i='${indep:?}' and d='${dep:?}' 
        and formula='${formula:?}'
        and p<0.01 
        and (warnings = 0)" \
				      --tables r "${1:?file}")
		err=$?; test $err -ne 0 && break
		log_debug "coefs, ors: $coefs_ors"
		printf '%s\n' "${coefs_ors:?}" \
		    |mean-sd-se.R -c COEF --digits 3 --identifier "${indep:?}" \
				  > "${averag_coefs_tmp:?}"
		err=$?; test $err -ne 0 && break
		
		printf '%s\n' "${coefs_ors:?}" \
		    |mean-sd-se.R -c ODDSRATIO --digits 2 --identifier "${indep:?}" \
				  > "${averag_ors_tmp:?}"
		err=$?; test $err -ne 0 && break
		
		joint=$(csvsql -d ',' -q '"' --query "
select '${dep:?}' as D, '${formula:?}' as FORMULA, '${indep:?}' as I, M_COEF, SD_COEF, M_ODDSRATIO as M_OR, SD_ODDSRATIO as SD_OR, coefs.N as N 
from coefs
join ors on coefs.Identifier = ors.Identifier
" --tables coefs,ors "${averag_coefs_tmp:?}" "${averag_ors_tmp:?}")
		err=$?; test $err -ne 0 && break
		log_debug "joint: $joint"
		printf '%s\n' "$joint"|tail -n "$skip_header"
		skip_header='+2'
	    done
	done
    done
    rm -f -- "${averag_coefs_tmp:?}"
    rm -f -- "${averag_ors_tmp:?}"
    ###rm -f -- "${averag_mcfaddens_tmp:?}"
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
