#!/usr/bin/env sh

usage()
{
    me=$(basename -- "$0")
    echo "Usage: $me [--header] PROJECT_NAME OLD_SNAPSHOT_DATE NEW_SNAPSHOT_DATE"2
    echo "Usage example: $me openldap 2007-11-13 2008-07-19"
}

PRINT_HEADER=false
case x$1 in
    x-*) case "x$1" in
	     x--header) PRINT_HEADER=true
			shift;;
	     *) echo "Invalid option: \`$1'" >&2
		usage >&2
		exit 1
	 esac
esac

PROJECT=${1}
OLD=${2}
NEW=${3}

die_if_arg_missing()
{
    if [ -z "$1" ]
    then
	echo "$2 missing" >&2
	usage >&2
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

get_count_2()
{
    csvsql -d ',' -q '"' --tables old,new --query "${1:?query missing}" "$old_file" "$new_file"|tail -n +2
    if [ $? -ne 0 ]
    then
	echo "Error for query $query on files $old_file $new_file."  >&2
	exit 1
    fi
}

oldc=$(csvsql -d ',' -q '"' --tables old --query \
       'select count(*) from old' "$old_file"|tail -n +2)

newc=$(csvsql -d ',' -q '"' --tables new --query \
       'select count(*) from new' "$new_file"|tail -n +2)

identicalc=$(get_count_2 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file')

addedc=$(get_count_2 'select count(*) from new left join old on old.function_signature = new.function_signature and old.file = new.file where old.function_signature is null')

removedc=$(get_count_2 'select count(*) from old left join new on old.function_signature = new.function_signature and old.file = new.file where new.function_signature is null')

# Percent of annotation bundles added/removed
percent_add_rem=$(expr '(' ${addedc:?} '+' ${removedc:?} ')' '*' 100 / ${oldc:?})

# Changed feature location values: NOFL
ch_nofl_c=$(get_count_2 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file and old.nofl != new.nofl')
percent_ch_nofl=$(expr ${ch_nofl_c:?} '*' 100 / ${identicalc:?})

# Changed feature constants values: NOFC_NonDup
ch_nofc_nondup_c=$(get_count_2 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file and old.nofc_nondup != new.nofc_nondup')
percent_ch_nofc_nondup=$(expr ${ch_nofc_nondup_c:?} '*' 100 / ${identicalc:?})

# Changed nesting value: NONEST
ch_nonest_c=$(get_count_2 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file and old.nonest != new.nonest')
percent_ch_nonest=$(expr ${ch_nonest_c:?} '*' 100 / ${identicalc:?})

if $PRINT_HEADER
then
    printf 'DATE,AB_BEFORE,AB_NOW,AB_IDENTICAL,AB_ADDED,AB_REMOVED,AB_CH_NOFL,AB_CH_NOFC_NONDUP,AB_CH_NONEST,AB_PERC_ADD_REM,AB_PERC_CH_NOFL,AB_PERC_CH_NOFC_NONDUP,AB_PERC_CH_NONEST\n'
fi
printf '%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n' "${NEW:?}" ${oldc:?} ${newc:?} ${identicalc:?} ${addedc:?} ${removedc:?} ${ch_nofl_c:?} ${ch_nofc_nondup_c:?} ${ch_nonest_c:?} ${percent_add_rem:?} ${percent_ch_nofl:?} ${percent_ch_nofc_nondup:?} ${percent_ch_nonest:?}
