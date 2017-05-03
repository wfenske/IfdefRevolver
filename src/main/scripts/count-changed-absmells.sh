#!/usr/bin/env sh

PROJECT=${1}
OLD=${2}
NEW=${3}

usage()
{
    me=$(basename -- "$0")
    echo "Usage: $me PROJECT_NAME OLD_SNAPSHOT_DATE NEW_SNAPSHOT_DATE" >&2
    echo "Usage example: $me openldap 2007-11-13 2008-07-19" >&2
}

die_if_arg_missing()
{
    if [ -z "$1" ]
    then
	echo "$2 missing" >&2
	usage
	exit 1
    fi
}

die_if_arg_missing "$PROJECT" "Project name"
die_if_arg_missing "$OLD" "Old snapshot date"
die_if_arg_missing "$NEW" "New snapshot date"

old_file=results/$PROJECT/$OLD/ABRes.csv
new_file=results/$PROJECT/$NEW/ABRes.csv

if [ ! -f "$old_file" ]
then
    echo "No such file: $old_file" >&2
    exit 1
fi

if [ ! -f "$new_file" ]
then
    echo "No such file: $new_file" >&2
    exit 1
fi

csvsql -d ',' -q '"' --tables old --query \
       'select count(*) from old' "$old_file"|tail -n +2|xargs printf '%13s: %4d\n' "Old ABs"

csvsql -d ',' -q '"' --tables new --query \
       'select count(*) from new' "$new_file"|tail -n +2|xargs printf '%13s: %4d\n' "New ABs"

csvsql -d ',' -q '"' --tables old,new --query \
       'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file' "$old_file" "$new_file"|tail -n +2|xargs printf '%13s: %4d\n' "Identical ABs"

csvsql -d ',' -q '"' --tables old,new --query \
       'select count(*) from new left join old on old.function_signature = new.function_signature and old.file = new.file where old.function_signature is null' "$old_file" "$new_file"|tail -n +2|xargs printf '%13s: %4d\n' "Added ABs"

csvsql -d ',' -q '"' --tables old,new --query \
       'select count(*) from old left join new on old.function_signature = new.function_signature and old.file = new.file where new.function_signature is null' "$old_file" "$new_file"|tail -n +2|xargs printf '%13s: %4d\n' "Removed ABs"
