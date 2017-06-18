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
    echo " $me [-vq] -p PROJECT [-n PRETTY_NAME]"
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
    echo "Summarize the latest snapshot of project."
    echo
    echo "Example:"
    echo " $me -p apache"
    echo 
    echo "Options:"
    echo " -p PROJECT Name of the project to analyze. The input data is assumed to be"
    echo "            located in folders named snapshots/<PROJECT>/<YYYY-MM-DD> below the"
    echo "            current working directory."
    echo " -n         Pretty project name for the output."
    echo " -v         Print more log messages."
    echo " -q         Print less log messages."
    echo " -h         Print this help screen and exit."
    echo 
    echo "Exit code is 0 on success, non-zero otherwise."
}

## Purpose: reformate a date from format YYYY-MM-DD to MM/YYYY
## Usage: reformat_date <date>
reformat_date()
{
    printf '%s\n' "${1:?}"|sed 's,\([0-9][0-9][0-9][0-9]\)-\([0-9][0-9]\)-\([0-9][0-9]\),\2/\1,'|sed 's/^0//'
}

unset o_project
unset o_name

while getopts "hvqp:n:" o
do
    case "$o" in
	h) help
	   exit 0
	   ;;
	q) o_log_level=$(( $o_log_level - 1 ))
	   ;;
	v) o_log_level=$(( $o_log_level + 1 ))
	   ;;
	p) if [ -z "$OPTARG" ]
	   then
	       log_error "Project name must not be empty." >&2
	       usage_and_die
	   fi
	   o_project="$OPTARG"
	   ;;
	n) if [ -z "$OPTARG" ]
	   then
	       log_error "Pretty project name must not be empty." >&2
	       usage_and_die
	   fi
	   o_name="$OPTARG"
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

if [ -z ${o_name+x} ]
then
    log_info "No pretty project name given, assuming project name, \`$o_project'"
    o_name="$o_project"
fi

if [ $# -ne 0 ]
then
    log_warn "Ignoring positional arguments (none were expected): \`$@'"
fi

### Get start and end date and number of .c files
proj_info_file="results/${o_project:?}/projectInfo.csv"
log_info "Getting start and end date and number of .c files from ${proj_info_file:?}"

if [ ! -e "$proj_info_file" ]
then
    edie "No such file or directory: $proj_info_file"
fi

if [ ! -r "$proj_info_file" ]
then
    edie "Cannot read file: $proj_info_file"
fi

## NOTE, 2017-06-17, wf: There's some warning about a missing header
## row. That's why we redirect stderr to /dev/null
start_end_files=$(csvsql -q '"' -d ',' -H --tables info --query 'select mm.start_date,mm.end_date,info.c as end_files from info join (select min(b) start_date, max(b) end_date from info) as mm on info.b = mm.end_date' "${proj_info_file:?}" 2>/dev/null)
if [ $? -ne 0 ]
then
    edie "Failed to gather start and end date and number of files from ${proj_info_file:?}"
fi
log_debug "$start_end_files"

## Result will look sth. like this:
##
## start_date,end_date,end_files
## 1996-01-14,2017-01-27,320

## Remove header
start_end_files=$( printf '%s\n' "$start_end_files"|tail -n +2 )

start_snapshot=$( printf '%s\n' "$start_end_files"|cut -d ',' -f 1 )
end_snapshot=$( printf '%s\n' "$start_end_files"|cut -d ',' -f 2 )
num_files=$( printf '%s\n' "$start_end_files"|cut -d ',' -f 3 )

### Get #funcs etc. of last snapshot
end_snapshot_file="results/${o_project:?}/${end_snapshot:?}/joint_function_ab_smell_snapshot.csv"
log_info "Getting number functions and so on of last snapshot from ${end_snapshot_file:?}"

funcs_fkloc_floacratio=$(csvsql -q '"' -d ',' --tables subject --query 'select count(*) functions, sum(FUNCTION_LOC) / 1000.0 as kloc, sum(LOAC) * 100 / sum(FUNCTION_LOC) as loacratio from subject' "$end_snapshot_file")
if [ $? -ne 0 ]
then
    edie "Failed to get statistics of last snapshot from ${end_snapshot_file:?}"
fi
log_debug "$funcs_fkloc_floacratio"

## Remove header
funcs_fkloc_floacratio=$( printf '%s\n' "$funcs_fkloc_floacratio"|tail -n +2 )

num_funcs=$( printf '%s\n' "$funcs_fkloc_floacratio"|cut -d ',' -f 1 )
fkloc=$(     printf '%s\n' "$funcs_fkloc_floacratio"|cut -d ',' -f 2 )
flocratio=$( printf '%s\n' "$funcs_fkloc_floacratio"|cut -d ',' -f 3 )

### Get % of annotated funcs of last snapshot
log_info "Getting percentage of annotated functions of last snapsnot from ${end_snapshot_file:?}"

feature_funcs_ratio=$(csvsql -q '"' -d ',' --tables subject --query \
			     'select (select count(*) from subject where NOFL > 0) * 100.0 / count(*) as annotated_funcs_percent from subject' \
		      "${end_snapshot_file:?}" )
if [ $? -ne 0 ]
then
    edie "Failed to get statistics of last snapshot from ${end_snapshot_file:?}"
fi
log_debug "$feature_funcs_ratio"

## Remove header
feature_funcs_ratio=$( printf '%s\n' "$feature_funcs_ratio"|tail -n +2 )


### Determine number of all commits, irrespective of whether they are
### merges or modify .c files
repo_dir=repos/"${o_project:?}"
num_all_commits=$(cd "${repo_dir:?}" && git rev-list --all --count)
if [ $? -ne 0 ]
then
    edie "Failed to count commits in repository ${repo_dir:?}"
fi

### Determine number of relevant commits (non-merge, .c-modifying commits)
commits_file="results/${o_project:?}/revisionsFull.csv"
log_info "Determining number of relevant commits from ${commits_file:?}"

num_relevant_commits=$(wc -l "${commits_file:?}")
if [ $? -ne 0 ]
then
    edie "Failed to count commits in ${commit_file:?}"
fi
# The output also contains the file name, which we dont need.
num_relevant_commits=$(printf '%s\n' "${num_relevant_commits:?}"|sed 's/[[:space:]]*\([[:digit:]]\{1,\}\).*/\1/')
# The file has a header row, so we need to subtract 1
num_relevant_commits=$(( $num_relevant_commits - 1 ))

### Reformat the dates
out_start_date=$( reformat_date "$start_snapshot" )
out_end_date=$( reformat_date "$end_snapshot" )

### Final output
export LC_NUMERIC=C
log_info "Statistics for project $o_project"
log_info "Format: <name> & start-date & end-date & commits & files & funcs & (%annotated funcs) & fkloc & floac%"
printf '%9s & %7s & %7s & %d & %5d & %6d & (%.1f\\,\\%%) & %5.1f & (%.1f\\,\\%%)\n' \
       "${o_name}" "$out_start_date" "$out_end_date" $num_all_commits $num_files $num_funcs "$feature_funcs_ratio" "$fkloc" "$flocratio"
