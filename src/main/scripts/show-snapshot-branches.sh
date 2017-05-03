#!/usr/bin/env sh

PROJECT="${1:?no project name}"

for f in results/$PROJECT/snapshots/*.csv
do
    date=$(basename $f)
    date=${date%.csv}
    firstcommit=$(head -n 2 $f | tail -n +2)
    branch_pos_commit=$(grep -F "$firstcommit" results/$PROJECT/revisionsFull.csv|cut -d, -f1,2,3)
    printf '%s,%s\n' "$date" "$branch_pos_commit"
done
