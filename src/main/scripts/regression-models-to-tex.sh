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
    echo " $me [-d DIR] -f=full|loc -s=full|annotated|changed|annotated-changed"
    echo "Get help by executing:"
    echo " $me -h"
}

usage_and_die()
{
    usage >&2
    exit 1
}

o_dir=.
o_formula=full
o_dataset=full
o_example_project=openldap

help()
{
    usage
    echo 
    echo "Create LaTeX code for tables summarizing the regression models."
    echo
    echo "Example:"
    echo " $me -d results -f full -d full -s apache"
    echo 
    echo "Options:"
    echo " -h         Print this help screen and exit."
    echo " -C DIR     results directory. If ommitted, the working directory is used."
    echo " -f FORMULA Use full formula (\`full') or just LOC-based formula (\`loc')."
    echo "            [default: $o_formula]"
    echo " -d DATASET Type of dataset to use. Valid value are:"
    echo "            - \`full':              use all functions,"
    echo "            - \`annotated':         only use the annotated functions,"
    echo "            - \`changed':           only use the changed functions,"
    echo "            - \`annotated-changed': only use the annotated and changed"
    echo "                                   functions."
    echo "            [default: $o_dataset]"
    echo " -p SYSTEM  Subject system to use as an example. [default: $o_example_project]"
    echo 
    echo "Exit code is 0 on success, non-zero otherwise."
}

while getopts "hC:f:p:d:" o
do
    case "$o" in
	h) help
	   exit 0
	   ;;
	C) if [ -z "$OPTARG" ]
	   then
	       log_error "Directory name must not be empty." >&2
	       usage_and_die
	   fi
	   o_dir="$OPTARG"
	   ;;
	d) case "$OPTARG" in
	       full|annotated|changed|annotated-changed) o_dataset="$OPTARG";;
	       *) log_error "Invalid value for dataset option (\`-d'): $OPTARG" >&2
		  usage_and_die
		  ;;
	   esac
	   ;;
	f) case "$OPTARG" in
	       full|loc) o_formula="$OPTARG";;
	       *) log_error "Invalid value for formula option (\`-f'): $OPTARG" >&2
		  usage_and_die
		  ;;
	   esac
	   ;;
	p) if [ -z "$OPTARG" ]
	   then
	       log_error "Subject system must not be empty (option \`-p')" >&2
	       usage_and_die
	   fi
	   o_example_project="$OPTARG"
	   ;;
	*) usage_and_die
	   ;;
    esac
done
shift $((OPTIND-1))

if [ $# -ne 0 ]
then
    log_warn "Ignoring positional arguments. Expected none, got $#: $@." >&2
    usage_and_die
fi

the_formula=""
case "$o_formula" in
    full) the_formula="FL+FC+ND+NEG+LOACratio+log2LOC";;
    loc) the_formula="log2LOC";;
    *) edie "Internal error: formula type not supported: $o_formula";;
esac

##output_delimiter=$(printf '\t\\&\t')
output_delimiter=$(printf ' \\& ')
res=$(csvsql --tables rs,ra \
	    --query "
       select printf(\" % .3f\", ra.COEF) as EX_COEF,
              printf(\"% 7.2f\", ra.Z) as '   EX_Z',
              (case when ra.p < 0.001 then '\$\\ll 0.01$'
                    else printf(\"   \$%.3f\$\", ra.p) end) as '      EX_P',
              printf(\"% .3f\", M_COEF) as M_COEF,
              printf(\"  %.3f\", SD_COEF) as SD_COEF,
              printf(\"% 7.2f\", rs.M_Z) as '    M_Z',
              printf(\"%5.2f\", rs.SD_Z) as ' SD_Z',
              rs.N as N,
              rs.D as D,
              rs.I as I,
	      ra.FORMULA as FORMULA
       from rs join ra on rs.i=ra.i
       	    and rs.d=ra.d
	    and rs.formula=ra.formula
	    and ra.system='${o_example_project:?}'
       where rs.formula='${the_formula:?}'" \
	    "$o_dir/regression-${o_dataset}-summary.csv" "$o_dir/regression-${o_dataset}-all.csv" )

if [ $? -ne 0 ]
then
    edie "Aggregation failed (see previous messages for details)"
fi

printf '%s\n' "$res" | sed "s/,/$output_delimiter/g"
