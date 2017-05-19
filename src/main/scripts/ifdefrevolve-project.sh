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
    echo " $me -p PROJECT_NAME [-w WINDOW_SIZE] [-n]"
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
    lo_windowsize=
    lo_dry_run=
    indent=

    if $have_long_opt
    then
	   lo_project=', --project   '
	lo_windowsize=', --windowsize'
	   lo_dry_run=', --dry-run   '
	       indent='              '
    fi
    
    usage
    echo
    echo "Options:"
    echo " -p${lo_project} PROJECT_NAME Name of the project to analyze"
    echo " -s${lo_windowsize} WINDOW_SIZE  Number of commits for a commit window."
    echo "   ${indent}              See createsnapshots.sh(1) to see what the default value is."
    echo " -n${lo_dry_run}              Print the commands that would be executed, but do not execute"
    echo "   ${indent}              them."
    echo
    echo "Example:"
    echo " $me -p openldap"
}

short_opts="p:s:hn"
case $getop_flavor in
    gnu) ARGS=`getopt -o "$short_opts" -l "project:,windowsize:,help,dry-run" -n "$me" -- "$@"`
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

eval set -- "$ARGS"

while true
do
    case $1 in
	-s|--windowsize)
	    shift
	    WINDOW_SIZE=$1
	    shift
	    ;;
	-p|--project)
	    shift
	    PROJECT=$1
	    have_project=true
	    shift
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

make -f "${real_me_dir}/ifdefrevolve-project.mk" $DRY_RUN
