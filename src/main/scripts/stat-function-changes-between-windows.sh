#!/usr/bin/env sh

echo_as_me()
{
    me=$(basename -- "$0")
    echo "$me: $@"
}

O_ALL=--all
O_ANNOTATED=--annotated

usage()
{
    echo "Usage: $me $O_ALL|$O_ANNOTATED PROJECT_NAME"
    echo "Usage example: $me $O_ANNOTATED openldap"
}


MODE=
case x$1 in
    x-*) case "x$1" in
	     x$O_ANNOTATED) MODE=annotated
			   shift;;
	     x$O_ALL) MODE=all
		     shift;;
	     *) echo_as_me "Invalid option: \`$1'" >&2
		usage >&2
		exit 1
	 esac
esac

if [ -z "$MODE" ]
then
    echo_as_me "No mode selected. Need to specify one of $O_ALL (all functions), $O_ANNOTATED (only annotated functions)." >&2
    usage >&2
    exit $?
fi

if [ $# -ne 1 ]
then
    echo_as_me "Invalid number of command line arguments. Expected 1, got: $#" >&2
    usage >&2
    exit 1
fi

PROJECT=${1:?missing system name}

# Determine the dates of the longest branch in the system
echo "# Fetching dates of longest branch of $PROJECT..." >&2
dates=$(show-snapshot-branches.sh "$PROJECT"|csvsql -d ',' -q '"' --table branches --query 'select date from branches where branch = (select branch from branches where position = (select max(position) from branches) limit 1)'|tail -n +2) || exit $?

count_changed_ab_smells()
{
    echo "# Determining changed annotated functions of $PROJECT ..." >&2
    last_date=$1
    shift
    o_header=--header
    for date in $@
    do
	echo "# $last_date -> $date" >&2
	count-changed-absmells.sh $o_header $PROJECT $last_date $date || break
	o_header=
	last_date=$date
    done
}

count_changed_functions()
{
    echo "# Determining changed functions (with or without annotations) of $PROJECT ..." >&2
    last_date=$1
    shift
    o_header=--header
    for date in $@
    do
	echo "# $last_date -> $date" >&2
	count-changed-functions.sh $o_header $PROJECT $last_date $date || break
	o_header=
	last_date=$date
    done
}

case $MODE in
    all) count_changed_functions $dates;;
    annotated) count_changed_ab_smells $dates;;
    *) echo_as_me "Internal error: Invalid mode: $MODE" >&2
       exit 1
       ;;
esac
