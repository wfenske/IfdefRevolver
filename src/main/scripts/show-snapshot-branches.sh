#!/usr/bin/env sh

me=$(basename -- "$0") || exit $?

PROJECT="${1:?no project name}"

printf 'DATE,BRANCH,POSITION,COMMIT\n'
for f in results/$PROJECT/snapshots/*.csv
do
    if [ ! -e "$f" ]
    then
	echo "$me: No such file or directory: $f" >&2
	exit 1
    fi
    
    if [ ! -r "$f" ]
    then
	echo "$me: Cannot read: $f" >&2
	exit 1
    fi

    date=$(basename $f)
    date=${date%.csv}
    firstcommit=$(head -n 2 $f | tail -n +2) || exit $?
    branch_pos_commit=$(grep -F "$firstcommit" results/$PROJECT/revisionsFull.csv|cut -d, -f1,2,3) || exit $?
    printf '%s,%s\n' "$date" "$branch_pos_commit"
done
