#!/usr/bin/env sh

real_me=$(realpath -- $0) || exit $?
real_me_dir=$(dirname -- "$real_me") || exit $?

. "${real_me_dir}"/lib-count-changed-functions.sh || exit $?

old_file=results/$PROJECT/$OLD/ABRes.csv
new_file=results/$PROJECT/$NEW/ABRes.csv

die_if_file_missing "$old_file"
die_if_file_missing "$new_file"
die_if_file_identical "$old_file" "$new_file"

old_ch_file=results/$PROJECT/$OLD/joint_function_ab_smell_snapshot.csv
new_ch_file=results/$PROJECT/$NEW/joint_function_ab_smell_snapshot.csv

die_if_file_missing "$old_ch_file"
die_if_file_missing "$new_ch_file"
die_if_file_identical "$old_ch_file" "$new_ch_file"

oldc=$(csvsql -d ',' -q '"' --tables old --query \
       'select count(*) from old' "$old_file"|tail -n +2)

newc=$(csvsql -d ',' -q '"' --tables new --query \
       'select count(*) from new' "$new_file"|tail -n +2)

identicalc=$(get_count_2 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file')

addedc=$(get_count_2 'select count(*) from new left join old on old.function_signature = new.function_signature and old.file = new.file where old.function_signature is null')

removedc=$(get_count_2 'select count(*) from old left join new on old.function_signature = new.function_signature and old.file = new.file where new.function_signature is null')

# Percent of annotation bundles added/removed
percent_add_rem=$(expr '(' ${addedc:?} '+' ${removedc:?} ')' '*' 100 / ${oldc:?})

# Number of functions with annotations existing in both snapshots and
# having received commit in the first snaphsot
changed_annotated_func_c=$(get_count_2 --changed 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file where old.commits > 0 and (old.nofl > 0 or new.nofl > 0)')

# Changed feature location values: NOFL
ch_nofl_c=$(get_count_2 --changed 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file and old.nofl != new.nofl where old.commits > 0')
percent_ch_nofl=$(expr ${ch_nofl_c:?} '*' 100 / ${changed_annotated_func_c:?})

# Changed feature constants values: NOFC_NonDup
ch_nofc_nondup_c=$(get_count_2 --changed 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file and old.nofc_nondup != new.nofc_nondup where old.commits > 0')
percent_ch_nofc_nondup=$(expr ${ch_nofc_nondup_c:?} '*' 100 / ${changed_annotated_func_c:?})

# Changed nesting value: NONEST
ch_nonest_c=$(get_count_2 --changed 'select count(*) from new join old on old.function_signature = new.function_signature and old.file = new.file and old.nonest != new.nonest where old.commits > 0')
percent_ch_nonest=$(expr ${ch_nonest_c:?} '*' 100 / ${changed_annotated_func_c:?})

if $PRINT_HEADER
then
    printf 'DATE,AB_BEFORE,AB_NOW,AB_IDENTICAL,AB_ADDED,AB_REMOVED,AB_CH_NOFL,AB_CH_NOFC_NONDUP,AB_CH_NONEST,AB_PERC_ADD_REM,AB_PERC_CH_NOFL,AB_PERC_CH_NOFC_NONDUP,AB_PERC_CH_NONEST\n'
fi

printf '%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d\n' "${NEW:?}" ${oldc:?} ${newc:?} ${identicalc:?} ${addedc:?} ${removedc:?} ${ch_nofl_c:?} ${ch_nofc_nondup_c:?} ${ch_nonest_c:?} ${percent_add_rem:?} ${percent_ch_nofl:?} ${percent_ch_nofc_nondup:?} ${percent_ch_nonest:?}
