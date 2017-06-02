#!/usr/bin/env sh

real_me=$(realpath -- $0) || exit $?
real_me_dir=$(dirname -- "$real_me") || exit $?

. "${real_me_dir}"/lib-count-changed-functions.sh || exit $?

old_file=results/$PROJECT/$OLD/all_functions.csv
new_file=results/$PROJECT/$NEW/all_functions.csv

old_ch_file=results/$PROJECT/$OLD/joint_function_ab_smell_snapshot.csv
new_ch_file=results/$PROJECT/$NEW/joint_function_ab_smell_snapshot.csv

die_if_file_missing "$old_file"
die_if_file_missing "$new_file"
die_if_file_identical "$old_file" "$new_file"

oldc=$(csvsql -d ',' -q '"' --tables old --query \
       'select count(*) from old' "$old_file"|tail -n +2)

newc=$(csvsql -d ',' -q '"' --tables new --query \
       'select count(*) from new' "$new_file"|tail -n +2)

identicalc=$(get_count_2 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file')

addedc=$(get_count_2 'select count(*) from new left join old on old.function_signature = new.function_signature and old.file = new.file where old.function_signature is null')

removedc=$(get_count_2 'select count(*) from old left join new on old.function_signature = new.function_signature and old.file = new.file where new.function_signature is null')

# Percent of functions bundles added/removed
percent_add_rem=$(expr '(' ${addedc:?} '+' ${removedc:?} ')' '*' 100 / ${oldc:?})

# Median percent by which a function's LOC metric changes from one snapshot to the next
loc_changes=$(csvsql -d ',' -q '"' --query 'select abs(old.function_loc-new.function_loc) * 100.0 / old.function_loc as perc_loc_diff from old join new on old.function_signature=new.function_signature and old.file=new.file where old.commits > 0' --tables old,new "$old_ch_file" "$new_ch_file") || exit $?
median_loc_change=$(printf '%s\n' "$loc_changes"|averages.R -c perc_loc_diff -H) || exit $?

if $PRINT_HEADER
then
    printf 'DATE,FUNC_BEFORE,FUNC_NOW,FUNC_IDENTICAL,FUNC_ADDED,FUNC_REMOVED,FUNC_PERC_ADD_REM,MEDIAN_LOC_CHG_PERC\n'
fi

LC_NUMERIC=C; export LC_NUMERIC
printf '%s,%d,%d,%d,%d,%d,%d,%.2f\n' "${NEW:?}" ${oldc:?} ${newc:?} ${identicalc:?} ${addedc:?} ${removedc:?} ${percent_add_rem:?} "${median_loc_change:?}"
