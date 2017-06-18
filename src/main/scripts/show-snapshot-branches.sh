#!/usr/bin/env sh
me=$(basename -- "$0") || exit $?

LOG_LEVEL_SILENT=0
LOG_LEVEL_ERROR=1
LOG_LEVEL_WARN=2
LOG_LEVEL_INFO=3
LOG_LEVEL_DEBUG=4

o_log_level=$LOG_LEVEL_WARN
#o_log_level=$LOG_LEVEL_INFO
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
    echo " $me -p PROJECT"
    echo "Get help by executing:"
    echo " $me -h"
}

help()
{
    usage
    echo 
    echo "List the chain and first commit of each snapshot of an IfdefRevolver project."
    echo
    echo "Example:"
    echo " $me -p openldap"
    echo 
    echo "Options:"
    echo " -p PROJECT Name of the project. The input data is assumed to be"
    echo "            located in folders named results/<PROJECT>/<YYYY-MM-DD> below the"
    echo "            current working directory."
    echo " -v         Print more log messages."
    echo " -q         Print less log messages."
    echo " -h         Print this help screen and exit."
    echo 
    echo "Exit code is 0 in case of complete success and non-zero otherwise."
}

usage_and_die()
{
    usage >&2
    exit 1
}

unset o_project

while getopts "p:vqh" o
do
    case "$o" in
	p) if [ -z "$OPTARG" ]
	   then
	       log_error "Project name must not be empty." >&2
	       usage_and_die
	   fi
	   o_project="$OPTARG"
	   ;;
	q) o_log_level=$(( $o_log_level - 1 ))
	   ;;
	v) o_log_level=$(( $o_log_level + 1 ))
	   ;;
	h) help
	   exit 0
	   ;;
	*) usage_and_die
	   ;;
    esac
done
shift $((OPTIND-1))

if [ -z ${o_project+x} ]
then
    log_error "No project named." >&2
    usage_and_die
fi

if [ $# -ne 0 ]
then
    log_warn "Ignoring positional arguments (none were expected): \`$@'"
fi

revisions_file="results/$o_project/revisionsFull.csv"
snapshots_dir="results/$o_project/snapshots"

### Validate revisions file's existence
if [ ! -e "${revisions_file:?}" ]
then
    edie "No such file or directory: ${revisions_file:?}" 
fi

if [ ! -f "${revisions_file:?}" ]
then
    edie "Not a proper file: ${revisions_file:?}" 
fi
    
if [ ! -r "${revisions_file:?}" ]
then
    edie "Cannot read: ${revisions_file:?}"
fi

### Validate snapshots dir's existence
if [ ! -e "${snapshots_dir:?}" ]
then
    edie "No such file or directory: ${snapshots_dir:?}" 
fi

if [ ! -d "${snapshots_dir:?}" ]
then
    edie "Not a directory: ${snapshots_dir:?}" 
fi
    
if [ ! -r "${snapshots_dir:?}" ]
then
    edie "Cannot read: ${snapshots_dir:?}"
fi

### Actually do the work
printf 'DATE,BRANCH,POSITION,COMMIT\n'
for f in "${snapshots_dir:?}"/*.csv
do
    if [ ! -e "$f" ]
    then
	edie "No such file or directory: $f" 
    fi
    
    if [ ! -r "$f" ]
    then
	edie "Cannot read: $f"
    fi

    date=$(basename "$f")
    date=${date%.csv}
    firstcommit=$(head -n 2 "$f" | tail -n +2) || exit $?
    branch_pos_commit=$(grep -F "$firstcommit" "${revisions_file:?}") || edie "No match for commit ${firstcommit} in ${revisions_file:?}"
    branch_pos_commit=$(printf '%s\n' "$branch_pos_commit"|cut -d, -f1,2,3)
    printf '%s,%s\n' "$date" "$branch_pos_commit"
done
