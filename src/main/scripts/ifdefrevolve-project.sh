#!/usr/bin/env sh

me=$(basename -- "$0") || exit $?
real_me=$(realpath -- "$0") || exit $?
real_me_dir=$(dirname -- "$real_me") || exit $?

echo_as_me()
{
    echo "$me: $@"
}

usage()
{
    echo "Usage:"
    echo " $me -p PROJECT_NAME [-b] [-n]"
    echo " $me -h"
}

getop_flavor=gnu
case $(uname) in
    Darwin) getop_flavor=bsd
	    break
	    ;;
    Linux) getop_flavor=gnu
	   break
	   ;;
    *) getop_flavor=bsd
       break
       ;;
esac

have_long_opt=false
case $getop_flavor in
    gnu) have_long_opt=true;;
esac

help()
{
    lo_project=
    ##lo_windowsize=
    lo_dry_run=
    lo_background=
    indent=

    if $have_long_opt
    then
	   lo_project=', --project   '
	##lo_windowsize=', --windowsize'
	   lo_dry_run=', --dry-run   '
	   lo_background=', --background'
	       indent='              '
    fi
    
    usage
    echo
    echo "Options:"
    echo " -p${lo_project} PROJECT_NAME Name of the project to analyze"
    ##echo " -s${lo_windowsize} WINDOW_SIZE  Number of commits for a commit window."
    ##echo "   ${indent}              See createsnapshots.sh(1) to see what the default"
    ##echo "   ${indent}              value is."
    echo " -b${lo_background} Run the anaylsis in the in background."
    echo " -n${lo_dry_run} Print the commands that would be executed, but do"
    echo "   ${indent} not execute them."
    echo
    echo "Example:"
    echo " $me -p openldap"
}

short_opts="p:bhn"
case $getop_flavor in
    gnu) ARGS=`getopt -o "$short_opts" -l "project:,background:,help,dry-run" -n "$me" -- "$@"`
	 err=$?
	 break
	 ;;
    bsd|*) ARGS=`getopt "$short_opts" "$@"`
	 err=$?
	 break
	 ;;
esac

# Getopt error
if [ $err -ne 0 ]
then
    usage >&2
    exit $err
fi

WINDOW_SIZE=
PROJECT=
DRY_RUN=
have_project=false
background_mode=false

eval set -- "$ARGS"

while true
do
    case $1 in
#	-s|--windowsize)
#	    shift
#	    WINDOW_SIZE=$1
#	    shift
#	    ;;
	-p|--project)
	    shift
	    PROJECT=$1
	    have_project=true
	    shift
	    ;;
	-b|--background)
	    shift
	    background_mode=true
	    ;;
	-n|--dry-run)
	    shift
	    DRY_RUN=-n
	    ;;
	-h|--help)
	    shift
	    help
	    exit 0
	    ;;
	--) shift
	    break
	    ;;
    esac
done

if ! $have_project
then
    echo_as_me "Must specify a project." >&2
    usage >&2
    exit 1
fi

if  [ -z "$PROJECT" ]
then
    echo_as_me "Project name must not be empty." >&2
    usage >&2
    exit 1
fi

export PROJECT
if [ -n "$WINDOW_SIZE" ]
then
    export WINDOW_SIZE
else
    unset WINDOW_SIZE
fi

PATH=$PATH:${real_me_dir}

DAEMONIZE_CMD=daemonize
if $background_mode && ! $(command -v $DAEMONIZE_CMD >/dev/null 2>&1)
then
    echo_as_me "Cannot run in background mode.  Command \`$DAEMONIZE_CMD' not found.  Running in foreground mode instead."
    background_mode=false
fi

NUM_JOBS=2
export DRY_RUN

if $background_mode && [ -z "$DRY_RUN" ]
then
    LOG_FILE="${PROJECT}/logs/ifdefrevolve-project.log"
    mkdir -p "${PROJECT}/logs" || exit $?
    $DAEMONIZE_CMD bash -c "time nice make -f '${real_me_dir}/ifdefrevolve-project.mk' -j${NUM_JOBS} $DRY_RUN >> '${LOG_FILE}' 2>&1"
    echo_as_me "Started analysis of \`$PROJECT'. Log output is written to $LOG_FILE" 2>&1
else
    make -f "${real_me_dir}/ifdefrevolve-project.mk" -j${NUM_JOBS} $DRY_RUN
fi
