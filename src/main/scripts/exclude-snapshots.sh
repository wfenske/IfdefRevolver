#!/usr/bin/env sh

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
    echo " $me -p PROJECT [-nc] [SNAPSHOT_DATE]..."
    echo "Get help by executing:"
    echo " $me -h"
}

help()
{
    usage
    echo 
    echo "Manually exclude snapshots of IfdevRevolver projects. The snapshots are not"
    echo "physically removed, only moved to subdirectories so that further processing"
    echo "steps will ignore them."
    echo
    echo "Example:"
    echo " $me -p openldap 2011-06-29 2012-03-09"
    echo 
    echo "Options:"
    echo " -p PROJECT Name of the project. The input data is assumed to be"
    echo "            located in folders named results/<PROJECT>/<YYYY-MM-DD> below the"
    echo "            current working directory."
    echo " -c         Create missing directories instead of exiting with an error code."
    echo " -n         Show the commands that would be executed but do not actually execute them."
    echo " -h         Print this help screen and exit."
    echo 
    echo "Exit code is 0 in case of complete success. If the project source directories are missing, the exit code is Otherwise, the number of snapshots"
    echo "for which problems occurred is used as the exit code."
}

usage_and_die()
{
    usage >&2
    exit 1
}

handle_missing_project_dir()
{
    if $o_create_missing_dirs
    then
	if $o_dry_run_cmd mkdir -p -- "$1"
	then
	    log_debug "Successfully created directory $1"
	    true
	else
	    log_error "Failed to create directory $1"
	    false
	fi
    else
	log_error "Missing directory: $1"
	false
    fi
}

src_results_dir=
src_snapshots_csv_dir=
src_snapshots_dir=
init_project_dirs_or_die()
{
    src_results_dir=results/$o_project
    src_snapshots_csv_dir=results/$o_project/snapshots
    src_snapshots_dir=snapshots/$o_project
    err=false
    if [ ! -d "$src_results_dir" ]
    then
	handle_missing_project_dir "$src_results_dir" || err=true
    fi
    if [ ! -d "$src_snapshots_csv_dir" ]
    then
	handle_missing_project_dir "$src_snapshots_csv_dir" || err=true
    fi
    if [ ! -d "$src_snapshots_dir" ]
    then
	handle_missing_project_dir "$src_snapshots_dir" || err=true
    fi

    if $err
    then
	edie "Some required directories are missing. Aborting."
    fi
}

# usage: move_to_dir SRC TARGET_DIR
# The target directory is created if it does not already exits
move_to_dir()
{
    mtd_src=$1
    mtd_target_dir=$2
    log_debug "Moving $1 to $2 ..."

    if [ ! -e "$mtd_src" ]
    then
	log_warn "Cannot move $mtd_src: no such file or directory."
	return 1
    fi
    
    log_debug "Ensuring target directory exits"
    if [ -e "$mtd_target_dir" ]
    then
	if [ ! -d "$mtd_target_dir" ]
	then
	    log_warn "Cannot move ${mtd_src}: Target already exists but is not a directory: ${mtd_target_dir}"
	    return 1
	fi
    else
	${o_dry_run_cmd} mkdir -p -- "$mtd_target_dir"
	mtd_err=$?
	if [ $mtd_err -ne 0 ]
	then
	    log_warn "Cannot move $mtd_src: Failed to create target directory ${mtd_target_dir}."
	    return $mtd_err
	fi
    fi
    ${o_dry_run_cmd} mv $o_mv_opts -- "$mtd_src" "${mtd_target_dir}/"
}

# Purpose: remove the specified date from the project's projectInfo.csv file
# Usage: remove_from_project_info <YYYY-MM-DD>
# exit code is 0 on success, non-zero otherwise
remove_from_project_info()
{
    rfpi_project_info_csv=${src_results_dir:?}/projectInfo.csv
    rfpi_project_info_csv_bak=${rfpi_project_info_csv}.orig
    if [ ! -e "${rfpi_project_info_csv}" ]
    then
	log_warn "Cannot exclude snapshot from projectInfo.csv: No such file or directory: ${rfpi_project_info_csv}"
	return 1
    fi
    if [ ! -f "${rfpi_project_info_csv}" ]
    then
	log_warn "Cannot exclude snapshot from projectInfo.csv: Not a proper file: ${rfpi_project_info_csv}"
	return 1
    fi
    if [ ! -r "${rfpi_project_info_csv}" ]
    then
	log_warn "Cannot exclude snapshot from projectInfo.csv: Cannot read: ${rfpi_project_info_csv}"
	return 1
    fi
    if [ ! -e "${rfpi_project_info_csv_bak}" ]
    then
	if ! ${o_dry_run_cmd} cp -- "${rfpi_project_info_csv}" "${rfpi_project_info_csv_bak}"
	then
	    log_warn "Cannot exclude snapshot from projectInfo.csv: Backing up ${rfpi_project_info_csv} failed."
	    return 1
	fi
    fi
    rfpi_new_contents=$(grep -v -- "$1" "${rfpi_project_info_csv}")
    if [ -n "${o_dry_run_cmd}" ]
    then
	log_info "Removing lines matching $1 from ${rfpi_project_info_csv}"
    else
	printf '%s\n' "$rfpi_new_contents" > "${rfpi_project_info_csv}"
	if [ $? -ne 0 ]
	then
	    log_warn "Failed to update ${rfpi_project_info_csv}.  File may be corrupted."
	    return 1
	fi
    fi
    true
}

# Usage move_snapshot <YYYY-MM-DD>
# exit code is 0 on success, non-zero otherwise
move_snapshot()
{
    ms_snapshot=$1
    ms_errors=0
    if ! move_to_dir "$src_results_dir/$ms_snapshot" "$src_results_dir/excluded"
    then
       # Errors are already logged by `move_to_dir'
       ms_errors=$(( $ms_errors + 1 ))
    fi
    if ! move_to_dir "$src_snapshots_csv_dir/${ms_snapshot}.csv" "$src_results_dir/excluded/snapshots"
    then
       # Errors are already logged by `move_to_dir'
       ms_errors=$(( $ms_errors + 1 ))
    fi
    if ! move_to_dir "$src_snapshots_dir/${ms_snapshot}" "$src_snapshots_dir/excluded"
    then
       # Errors are already logged by `move_to_dir'
       ms_errors=$(( $ms_errors + 1 ))
    fi
    if ! remove_from_project_info "$1"
    then
       # Errors are already logged by `remove_from_project_info'
	ms_errors=$(( $ms_errors + 1 ))
    fi

    return $ms_errors
}

## Usage: update_exclusion_finished_marker $successes $errors
update_exclusion_finished_marker()
{
    
    uefm_file=${src_results_dir:?}/.last_window_exclusion
    if [ -e "$uefm_file" -a $1 -eq 0 -a $2 -eq 0 ]
    then
	# Marker exists and the current program run didn't change anything
	return 0
    fi
    ${o_dry_run_cmd} touch -- "$uefm_file"
    uefm_err=$?
    if [ $uefm_err -ne 0 ]
    then
	log_warn "Error updating/creating exclusion marker ${uefm_file}"
    else
	log_info "Successfully created/updated ${uefm_file}"
    fi
    
    return $uefm_err
}

unset o_project
o_mv_opts=-i
unset o_dry_run_cmd
o_create_missing_dirs=false

while getopts "p:nhc" o
do
    case "$o" in
	p) if [ -z "$OPTARG" ]
	   then
	       log_error "Project name must not be empty." >&2
	       usage_and_die
	   fi
	   o_project="$OPTARG"
	   ;;
	n) o_dry_run_cmd=echo
	   ;;
	c) o_create_missing_dirs=true
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

init_project_dirs_or_die

### Move the snapshots, which are given as positional arguments
errors=0
error_snapshots=
successes=0
while [ $# -gt 0 ]
do
    snapshot=$1
    shift
    log_debug "Excluding snapshot $snapshot"
    move_snapshot "$snapshot"
    if [ $? -ne 0 ]
    then
	errors=$(( $errors + 1 ))
	error_snapshots="${error_snapshots:+${error_snapshots} }$snapshot"
    fi
done

update_exclusion_finished_marker $successes $errors
exclusion_marker_err=$?

### Report how many snapshots worked or didn't
if [ $successes -gt 0 ]
then
    log_info "Successfully excluded $successes snapshot(s)."
elif [ $errors -eq 0 ]
then
    log_info "Nothing to do."
fi

if [ $errors -gt 0 ]
then
    if [ $successes -eq 0 ]
    then
	log_warn "No snapshots were excluded. See previous log messages for details."
    else
	log_warn "Not all snapshots were successfully excluded. See previous log messages for details."
    fi

    log_warn "Ploblems occurred for the following snapshots:"
    for es in $error_snapshots
    do
	echo "$es" >&2
    done
    
    exit $errors
fi

exit $exclusion_marker_err
