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
    echo " $me [-vq] -p PROJECT [-n PRETTY_NAME] [-f OUTPUT_FORMAT]"
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
    echo "Archive the snapshot and results data of a project."
    echo
    echo "Example:"
    echo " $me -p apache"
    echo 
    echo "Options:"
    echo " -p PROJECT Name of the project to pack. The data is assumed to be"
    echo "            located in the directories <PROJECT>/snapshots/<YYYY-MM-DD>"
    echo "            and <PROJECT>/results/<YYYY-MM-DD> below the current"
    echo "            working directory."
    echo " -v         Print more log messages."
    echo " -q         Print fewer log messages."
    echo " -f         Overwrite output files if they already exist."
    echo " -n         Simluation mode. Only display what would be done but"
    echo "            do not actually do it."
    echo " -h         Print this help screen and exit."
    echo 
    echo "Exit code is 0 on success, non-zero otherwise."
}

unset o_project
unset o_overwrite

o_overwrite=false
o_sim=false

while getopts "hqvp:fn" o
do
    case "$o" in
	h) help
	   exit 0
	   ;;
	q) o_log_level=$(( $o_log_level - 1 ))
	   ;;
	v) o_log_level=$(( $o_log_level + 1 ))
	   ;;
	f) o_overwrite=true
	   ;;
	n) o_sim=true
	   ;;
	p) if [ -z "$OPTARG" ]
	   then
	       log_error "Project name must not be empty." >&2
	       usage_and_die
	   fi
	   o_project="$OPTARG"
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

MAYBE_SIM=
if $o_sim
then
    MAYBE_SIM=echo
fi

## Syntax: pack1 src dest
pack1()
{
    src=$1
    dest=$2

    log_debug "$src -> $dest ..."
	
    if [ -e "$dest" ]
    then
	if $o_overwrite
	then
	    log_info "Output file \`$dest' already exists. Overwriting ..."
	    ${MAYBE_SIM} rm -f "$dest"
	else
	    log_info "Output file \`$dest' already exists. Will not be overwritten."
	    return 1
	fi
    fi

    odir=$(dirname "$dest")
    ${MAYBE_SIM} mkdir -p "$odir"
    ${MAYBE_SIM} tar -czf "${dest}" "${src}"
    if [ $? -eq 0 ]
    then
	return 0
    else
	log_warn "Error packing \`${src}' into \`${dest}'."
	return 2
    fi
}

pack_date_dirs()
{
    subdir_in=$1
    subdir_out=$2
    
    n_found=0
    n_packed=0
    
    for d in ${subdir_in}/[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]
    do
	[ ! -d "$d" ] && continue

	n_found=$(( $n_found + 1 ))
	base=$(basename "$d")
    
	o="${subdir_out}_${base}.tar.gz"
	pack1 "$d" "$o"
	if [ $? -eq 0 ]
	then
	    n_packed=$(( $n_packed + 1 ))
	fi
    done

    if [ $n_found -eq 0 ]
    then
	log_warn "No matching directories in ${subdir_in}."
    fi

    return $n_packed
}

basedir_out=".archive/$o_project"

log_info "Archiving snapshots ..."
pack_date_dirs "$o_project/snapshots" "${basedir_out}/snapshots"
n_snapshots_packed=$?
if [ ${n_snapshots_packed} -eq 0 ]
then
    log_info "No snapshots were archived."
else
    log_info "Successfully archived ${n_snapshots_packed} snapshots."
fi

log_info "Archiving results subdirectories ..."
pack_date_dirs "$o_project/results" "${basedir_out}/results"
n_results_packed=$?
if [ ${n_results_packed} -eq 0 ]
then
    log_info "No results subdirectories were archived."
else
    log_info "Successfully archived ${n_results_packed} results subdirectories."
fi

log_info "Archiving additional results files ..."
for ext in age bak csv mk nofc pdf rds
do
    for f in "$o_project/results"/*.${ext}
    do
	if [ -e "$f" ]
	then
	    fbase=$(basename "$f")
	    pack1 "$f" "${basedir_out}/results_${fbase}.tar.gz"
	fi
    done
done

log_info "Archiving logs ..."
if [ -d "$o_project/logs" ]
then
    pack1 "$o_project/logs" "$basedir_out/logs.tar.gz"
else
    log_war "No log dirs were found."
fi
