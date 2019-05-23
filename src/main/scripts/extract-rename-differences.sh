#!/usr/bin/env sh

(echo 'actual,threshold,oldname,newname';
grep -F --no-filename 'similar enough for a rename' "$@"|sed 's|INFO .*similar enough for a rename: \(.*\) -> \(.*\) threshold=\(.*\) actual distance=\(.*\)|\4,\3,\1,\2|'|sort -n|sed 's/^[[:space:]]*//'|uniq)|csvsql --tables t --query "select threshold-actual as diff,* from t order by diff"
