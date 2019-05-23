#!/usr/bin/env sh

PROJECT="$1"

csvcut -c diff,oldname,newname "${PROJECT}/results/renames-ordered-by-distance.csv"|while read line
do
    if [ "$line" = "diff,oldname,newname" ]
    then
	continue
    fi
    diff=${line%%,*}
    rest=${line#*,}
    newname=${rest#*,}
    oldname=${rest%,*}
    commit=$(grep --no-filename "$oldname.*,MOVE,.*$newname" "${PROJECT}/results"/*/function_change_hunks.csv|csvcut -H -c 4|tail -n1)
    if [ -z "$commit" ]
    then
	continue
    fi
    shorthash=$(git -C "repos/${PROJECT}/" rev-parse --short "$commit")
    printf '### distance %d: %s -> %s (commit: %s)\n' "${diff}" "$oldname" "$newname" "$shorthash"
    git -C "repos/${PROJECT}/" show "$commit"|grep --color -e '^---.*\.c$' -e '^+++.*\.c$' -e "^-.*\\<$oldname\\>" -e "^+.*\\<$newname\\>" -e '^@@'
done
