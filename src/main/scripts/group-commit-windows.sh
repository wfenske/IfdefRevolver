#!/usr/bin/env sh

me=$(basename -- "$0") || exit $?
real_me=$(realpath -- "$0") || exit $?
real_me_dir=$(dirname -- "$real_me") || exit $?

LOG_LEVEL_SILENT=0
LOG_LEVEL_ERROR=1
LOG_LEVEL_WARN=2
LOG_LEVEL_INFO=3
LOG_LEVEL_DEBUG=4

o_log_level=$LOG_LEVEL_INFO
##o_log_level=$LOG_LEVEL_DEBUG

fBranches=
fBranchesOrdered=
fStacked=

silently_remove_tmp_file()
{
    if [ -n "$1" ]
    then
	log_debug "Deleting temporary file $1"
	rm -f "$1"
    fi
}

silently_remove_tmp_files()
{
    silently_remove_tmp_file "$fBranches"
    silently_remove_tmp_file "$fBranchesOrdered"
    silently_remove_tmp_file "$fStacked"
}

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
    silently_remove_tmp_files
    exit 1
}

usage()
{
    echo "Usage:"
    echo " $me -p PROJECT -o DIR [-w WINDOWS]"
    echo " $me -h"
}

usage_and_die()
{
    usage >&2
    silently_remove_tmp_files
    exit 1
}

O_WINDOWS_DEFAULT=10

help()
{
    usage
    echo
    echo "Options:"
    echo " -p PROJECT Name of the project to analyze. The input data is assumed to be"
    echo "            located in folders named results/<PROJECT>/<YYYY-MM-DD> below the"
    echo "            current working directory."
    echo " -w WINDOWS Number of windows to combine. [default: $O_WINDOWS_DEFAULT]"
    echo " -o DIR     Directory where the results should be stored."
    echo " -h         Print this help screen and exit."
    echo
    echo "Example:"
    echo " $me -p openldap -o ../grouped-4 -w 4"
}

strip_csv_header()
{
    printf '%s' "$@"|tail -n+2
}

unset o_project
unset o_windows
unset o_output_dir

while getopts "p:w:o:h" o
do
    case "$o" in
	p) if [ -z "$OPTARG" ]
	   then
	       log_error "Project name must not be empty." >&2
	       usage_and_die
	   fi
	   o_project="$OPTARG"
	   ;;
	w) if [ -z "$OPTARG" ]
	   then
	       log_error "Window number must not be empty." >&2
	       usage_and_die
	   fi
	   o_windows="$OPTARG"
	   ;;
	o) if [ -z "$OPTARG" ]
	   then
	       log_error "Output directory must not be empty." >&2
	       usage_and_die
	   fi
	   o_output_dir="$OPTARG"
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

if [ -z ${o_windows+x} ]
then
    o_windows=$O_WINDOWS_DEFAULT
    log_debug "No number of windows to combine specified. Defaulting to $o_windows." >&2
fi

printf '%s' "$o_windows"|grep -q -e '^[1-9][0-9]*$' > /dev/null 2>&1
if [ $? -ne 0 ]
then
    log_error "Window number is not a positive integer: $o_windows" >&2
    usage_and_die
fi

if [ -z ${o_output_dir+x} ]
then
    log_error "No output directory given." >&2
    usage_and_die
fi

output_dir_base=${o_output_dir}/results/$o_project
if [ -e "$output_dir_base" ]
then
    edie "Cowardly refusing to overwrite existing results in $output_dir_base."
fi

log_info "Grouping windows of $o_project into ${output_dir_base}, $o_windows per group."

fBranches=$(mktemp branches.csv.XXXXXX) || edie "Failed to create temporary file."
fBranchesOrdered=$(mktemp branchesOrdered.csv.XXXXXX) || edie "Failed to create temporary file."
fStacked=$(mktemp stacked.csv.XXXXXX) || edie "Failed to create temporary file."

log_debug "Listing window branches in $o_project"
$real_me_dir/show-snapshot-branches.sh "$o_project" > $fBranches || edie "Listing branches in $o_project failed."
log_debug "Found" $(wc -l $fBranches) "lines of window/branch info."

log_debug "Calculating branch lengths"
csvsql -d ',' -q '"' --tables bo --query 'select STARTDATE,BRANCH,NUM_WINDOWS from (select BRANCH,min(DATE) as STARTDATE,count(*) as NUM_WINDOWS from branches group by BRANCH) order by STARTDATE' --tables branches $fBranches > $fBranchesOrdered || edie "Calculating branch lengths failed."

log_debug "Grouping window data by branch."
branch_names=$(csvcut -d ',' -q '"' -c BRANCH $fBranchesOrdered)
branch_names=$(strip_csv_header "$branch_names")

count_all_windows=0
count_skipped_windows=0
for branch_name in $branch_names
do
    dates=$(csvsql -d ',' -q '"' --query "select DATE from branches where branch = $branch_name" --tables branches $fBranches) || exit $?
    dates=$(strip_csv_header "$dates")
    num_windows=$(csvsql -d ',' -q '"' --query "select NUM_WINDOWS from branches_ordered where branch = $branch_name" --tables branches_ordered $fBranchesOrdered) || edie "Calculating number of windows for branch $branch_name failed."
    num_windows=$(strip_csv_header "$num_windows")
    count_all_windows=$(( $count_all_windows + $num_windows ))
    
    skip_lines=$(( $o_windows  + 1 ))
    num_joint_windows=$(( $num_windows / $o_windows ))
    skipped_windows=$(( $num_windows - ${num_joint_windows} * $o_windows ))
    count_skipped_windows=$(( $count_skipped_windows + $skipped_windows ))
    
    if [ $skipped_windows -eq $num_windows ]
    then
	log_debug "Skipping branch $branch_name entirely. Need at least $o_windows windows, only got $num_windows."
    else
	log_debug "Skipping ${skipped_windows} window(s) of branch $branch_name"
    fi
    
    for (( iwin=1; $iwin <= $num_joint_windows; iwin++ ))
    do
	group=$(printf '%s' "$dates"|head -n $o_windows)
	dates=$(printf '%s' "$dates"|tail -n+${skip_lines})
	pgroup=$(printf '%s' "$group"|tr '\n' ' '|sed 's/[[:space:]]\{2,\}/ /g')
	log_debug "Branch $branch_name merged window $iwin/$num_joint_windows: $pgroup"

	## Check whether all files exist
	for d in $group
	do
	    f=results/$o_project/$d/joint_function_ab_smell_snapshot.csv
	    if [ ! -e "$f" ]
	    then
		edie "File is missing: $f"
	    fi
	done

	## Get the first date of the group
	first_date=
	for d in $group
	do
	    first_date=$d
	    break
	done

	## Create the file containing all data for the windows in the group
	for d in $group; do echo results/$o_project/$d/joint_function_ab_smell_snapshot.csv; done|xargs csvstack -d ',' -q '"' > $fStacked || edie "Stacking data in $o_project failed for dates $pgroup."

	## Group the data by function signature and file
	rDir=$output_dir_base/$first_date
	mkdir -p "$rDir" || edie "Creating directory $rDir failed."
	fStackedGrouped=$rDir/joint_function_ab_smell_snapshot.csv
	if [ -e "$fStackedGrouped" ]
	then
	    log_warn "Output file already exists (will be overwritten): ${fStackedGrouped}."
	fi
	
	log_info "Grouping windows $pgroup into $fStackedGrouped"
	csvsql --query 'select min(SNAPSHOT_DATE) as SNAPSHOT_DATE, FUNCTION_SIGNATURE, FILE, avg(FUNCTION_LOC) as FUNCTION_LOC, sum(HUNKS) as HUNKS, sum(COMMITS) as COMMITS, sum(LINES_CHANGED) as LINES_CHANGED, sum(LINE_DELTA) as LINE_DELTA, sum(LINES_DELETED) as LINES_DELETED, sum(LINES_ADDED) as LINES_ADDED, avg(ifnull(LOAC,0)) as LOAC, avg(ifnull(LOFC,0)) as LOFC, avg(ifnull(NOFL,0)) as NOFL, avg(ifnull(NOFC_Dup,0)) as NOFC_Dup, avg(ifnull(NOFC_NonDup,0)) as NOFC_NonDup, avg(ifnull(NONEST,0)) as NONEST, count(*) as NUM_WINDOWS from stacked group by FUNCTION_SIGNATURE, FILE' --tables stacked $fStacked > "$fStackedGrouped"
	if [ $? -ne 0 ]
	then
	    rm -f "$fStackedGrouped"
	    edie "Failed to create ${fStackedGrouped}."
	fi
    done
done

silently_remove_tmp_files

log_info "Successfully grouped "$(( $count_all_windows - $count_skipped_windows ))"/${count_all_windows} windows, skipping $count_skipped_windows."
real_out_dir=$(realpath -- "$output_dir_base")
log_info "Results location: $real_out_dir"
true
