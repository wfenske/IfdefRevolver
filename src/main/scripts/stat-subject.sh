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

## Purpose: Get the value of a field from a simple CSV-like output (quoting and escaped are not supported!)
##
## Usage get_csv_field CSV_STRING FIELD_NUM
get_csv_field0()
{
    printf '%s\n' "$1"|cut -d ',' -f $2
}

## Purpose: Get the value of a named column from a CSV table with a
## header row and a single content row.  Remove the header.
##
## Usage get_csv_field CSV_STRING_WITH_HEADER COLUMN_NAME
get_csv_field()
{
    printf '%s\n' "$1"|csvcut -c "$2"|tail -n +2
}

## Purpose: reformat a date from format YYYY-MM-DD (possibly with
## some garbage following) to MM/YYYY
## 
## Usage: mm_yyyy_date_from_timestamp <date>
mm_yyyy_date_from_timestamp()
{
    printf '%s\n' "${1:?}"|sed 's,\([0-9][0-9][0-9][0-9]\)-\([0-9][0-9]\)-\([0-9][0-9]\).*,\\wfmmyy{\2}{\1},'|sed 's/{0\{1,\}/{/g'
}

thousep() {
    printf '%s\n' "$1"|perl -wpe '1 while s/(\d+)(\d\d\d)/$1\\thousep{}$2/;'
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
    o_name=$(printf '\\subject%s\n' "$o_project"|tr -d '[0-9]')
    log_info "No pretty project name given, assuming, \`$o_name'"
fi

if [ $# -ne 0 ]
then
    log_warn "Ignoring positional arguments (none were expected): \`$@'"
fi

### Get start and end date
proj_info_file="${o_project:?}/results/commitParents.csv"
log_info "Getting start and end date from ${proj_info_file:?}"

if [ ! -e "$proj_info_file" ]
then
    edie "No such file or directory: $proj_info_file"
fi

if [ ! -r "$proj_info_file" ]
then
    edie "Cannot read file: $proj_info_file"
fi

start_end_totalcommits=$(csvsql -q '"' -d ',' --tables commits --query "SELECT MIN(timestamp) AS starttime, MAX(timestamp) AS endtime, COUNT(distinct \`commit\`) AS total_commits FROM commits" "${proj_info_file:?}")
if [ $? -ne 0 ]
then
    edie "Failed to gather start and end date from ${proj_info_file:?}"
fi
log_debug "$start_end_totalcommits"

## Result will look sth. like this:
##
## starttime,endtime,total_commits
## 2005-09-26 05:28:27.000000,2019-01-22 15:50:32.000000,3309

starttime=$(get_csv_field "$start_end_totalcommits" starttime)
endtime=$(get_csv_field "$start_end_totalcommits" endtime)
##num_all_commits=$(get_csv_field "$start_end_totalcommits" total_commits)
num_all_commits=$(git -C "repos/${o_project:?}" rev-list HEAD --count)

### Reformat the dates
out_start_date=$( mm_yyyy_date_from_timestamp "$starttime" )
out_end_date=$( mm_yyyy_date_from_timestamp   "$endtime" )
log_debug "start date: $out_start_date"
log_debug "end date: $out_end_date"

### Determine number of relevant commits (non-merge, .c-modifying commits)
commits_file="${o_project:?}/results/revisionsFull.csv"
log_info "Determining number of relevant commits from ${commits_file:?}"

relevant_commits=$(csvsql -q '"' -d ',' --tables revs --query 'SELECT COUNT(DISTINCT commit_id) AS relevant_commits FROM revs' "${commits_file:?}")
if [ $? -ne 0 ]
then
    edie "Failed to count commits in ${commit_file:?}"
fi
log_debug "$relevant_commits"
num_relevant_commits=$(get_csv_field "$relevant_commits" relevant_commits)
log_debug "num_relevant_commits: $num_relevant_commits"

### Get date of last snapshot
snapshots_file="${o_project:?}/results/snapshots.csv"
log_info "Determining newest snapshot from ${snapshots_file:?}"

newest_snapshot_date=$(csvsql -q '"' -d ',' --tables snapshots --query 'SELECT snapshot_date FROM snapshots ORDER BY snapshot_date DESC LIMIT 1' "${snapshots_file:?}")
if [ $? -ne 0 ]
then
    edie "Failed to determine newest snapshot ${snapshots_file:?}"
fi
log_debug "$newest_snapshot_date"
newest_snapshot_date=$(get_csv_field "$newest_snapshot_date" SNAPSHOT_DATE)
log_debug "newest_snapshot_date: $newest_snapshot_date"

### Get #funcs etc. of last snapshot
all_functions_file="${o_project:?}/results/${newest_snapshot_date:?}/all_functions.csv"
abres_file="${o_project:?}/results/${newest_snapshot_date:?}/ABRes.csv"
log_info "Getting number files, functions, total LOC and LOAC of last snapshot from ${all_functions_file:?} and ${abres_file:?}"


tmp_all_funcs_file=$(mktemp -t stat-subject.all_funcs.XXXXXXXX.csv) || edie "Failed to create temp file"
tmp_ab_funcs_file=$(mktemp -t stat-subject.ab_funcs.XXXXXXXX.csv) || edie "Failed to create temp file"

csvsql -q '"' -d ',' --tables allf,ab --query "SELECT COUNT(DISTINCT allf.FILE) AS num_files, COUNT(*) AS total_functions, SUM(FUNCTION_LOC) AS total_loc, SUM(LOAC) as total_loac FROM allf LEFT JOIN ab ON allf.FILE = ab.FILE AND allf.FUNCTION_SIGNATURE=ab.FUNCTION_SIGNATURE" "$all_functions_file" "$abres_file" > "${tmp_all_funcs_file:?}"
if [ $? -ne 0 ]
then
    edie "Failed to get statistics of last snapshot from ${all_functions_file:?} and ${abres_file:?}"
fi

csvsql -q '"' -d ',' --tables ab --query "SELECT COUNT(*) AS ab_functions FROM ab" "$abres_file" > "${tmp_ab_funcs_file}"
if [ $? -ne 0 ]
then
    edie "Failed to get statistics of last snapshot from ${abres_file:?}"
fi

files_allfuncs_loc_loac_abfuncs=$(csvjoin -d ',' -q '"' "$tmp_all_funcs_file" "$tmp_ab_funcs_file")

log_debug "$files_allfuncs_loc_loac_abfuncs"
rm -f "$tmp_all_funcs_file" "$tmp_ab_funcs_file"

perc_abfuncs_perc_loac_kloc=$(printf '%s\n' "$files_allfuncs_loc_loac_abfuncs"|csvsql -d ',' -q '"' --tables t --query "SELECT CAST(ROUND((100.0 * ab_functions) / total_functions, 0) AS INT) perc_abfuncs,CAST(ROUND((100.0 * total_loac) / total_loc, 0) AS INT) perc_loac, CAST(ROUND(total_loc/1000.0, 0) AS INT) kloc FROM t" /dev/stdin)

log_debug "$perc_abfuncs_perc_loac_kloc"

num_files=$(get_csv_field "$files_allfuncs_loc_loac_abfuncs" num_files)
num_funcs=$(get_csv_field "$files_allfuncs_loc_loac_abfuncs" total_functions)
fkloc=$(get_csv_field     "$perc_abfuncs_perc_loac_kloc" kloc)
flocratio=$(get_csv_field "$perc_abfuncs_perc_loac_kloc" perc_loac)
feature_funcs_ratio=$(get_csv_field "$perc_abfuncs_perc_loac_kloc" perc_abfuncs)

domain_file="${o_project:?}/results/domain.csv"
domain=$(cat "$domain_file")
if [ $? -ne 0 ]
then
    edie "Failed to read domain from ${domain_file:?}"
fi
domain=$(get_csv_field "$domain" domain)

log_debug "num_files: $num_files"
log_debug "num_funcs: $num_funcs"
log_debug "fkloc: $fkloc"
log_debug "flocratio: $flocratio"
log_debug "feature_funcs_ratio: $feature_funcs_ratio"

out_num_all_commits=$(thousep "$num_all_commits")
out_num_files=$(thousep "$num_files")
out_num_funcs=$(thousep "$num_funcs")
out_fkloc=$(thousep "$fkloc")
out_feature_funcs_ratio=$(printf '\\subjectPercentAnnotatedFunctions{%d}\n' $feature_funcs_ratio)
out_flocratio=$(printf           '\\subjectPercentFunctionLoac{%d}\n'       $flocratio)

### Final output
export LC_NUMERIC=C
log_info "Statistics for project $o_project"
log_info "Format: <name> & start-date & end-date & commits & files & funcs & (%annotated funcs) & fkloc & floac & domain%"
printf             '%18s & %17s & %17s & %17s & %17s & %17s & %37s & %5s & %31s & %37s \\\\\n' \
       "${o_name}" "$out_start_date" "$out_end_date" "$out_num_all_commits" "$out_num_files" "$out_num_funcs" "$out_feature_funcs_ratio" "$out_fkloc" "$out_flocratio" "$domain"
