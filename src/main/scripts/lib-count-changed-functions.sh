echo_as_me()
{
    me=$(basename -- "$0")
    echo "$me: $@"
}

usage()
{
    echo "Usage: $me [--header] PROJECT_NAME OLD_SNAPSHOT_DATE NEW_SNAPSHOT_DATE"
    echo "Usage example: $me openldap 2007-11-13 2008-07-19"
}

PRINT_HEADER=false
case x$1 in
    x-*) case "x$1" in
	     x--header) PRINT_HEADER=true
			shift;;
	     *) echo_as_me "Invalid option: \`$1'" >&2
		usage >&2
		exit 1
	 esac
esac

if [ $# -ne 3 ]
then
    echo_as_me "Invalid number of command line arguments. Expected 3, got: $#" >&2
    usage >&2
    exit 1
fi

PROJECT=${1}
OLD=${2}
NEW=${3}

die_if_arg_missing()
{
    if [ -z "$1" ]
    then
	echo_as_me "$2 is empty" >&2
	usage >&2
	exit 1
    fi
}

die_if_file_identical()
{
    if [ "$1" -ef "$2" ]
    then
	echo_as_me "Files are identical: $1 and $2" >&2
	exit 1
    fi
}

die_if_file_missing()
{
    if [ ! -f "$1" ]
    then
	echo_as_me "No such file: \`$1'" >&2
	exit 1
    fi
}

get_count_2()
{
    csvsql -d ',' -q '"' --tables old,new --query "${1:?query missing}" "$old_file" "$new_file"|tail -n +2
    if [ $? -ne 0 ]
    then
	echo_as_me "Error for query $query on files $old_file $new_file."  >&2
	exit 1
    fi
}

die_if_arg_missing "$PROJECT" "Project name"
die_if_arg_missing "$OLD" "Old snapshot date"
die_if_arg_missing "$NEW" "New snapshot date"
