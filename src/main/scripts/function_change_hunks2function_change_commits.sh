#!/usr/bin/env sh

### Aggregate commit hunk info for each function to the level of
### individual commits.  The result is information about how much (in
### terms of number of hunks, LOC delta, LOC deleted, LOC added) each
### function was changed by each commit.

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")

aggregate()
{
    csvsql -d, -q '"' -y 10000 --query \
	   "SELECT
		FUNCTION_SIGNATURE,
		FILE,
		FUNCTION_LOC,
		COMMIT_ID,
		BUGFIX,
		COUNT(COMMIT_ID) as HUNKS,
		SUM(LINE_DELTA) as LINE_DELTA,
		SUM(LINES_DELETED) as LINES_DELETED,
		SUM(LINES_ADDED) as LINES_ADDED
       	   FROM stdin
       	   GROUP BY FUNCTION_SIGNATURE,FILE,FUNCTION_LOC,COMMIT_ID,BUGFIX"
}

AGG_INPUT_CSV=function_change_hunks.csv
AGG_OUTPUT_CSV=function_change_commits.csv

. "${me_dir}/function_change_aggregate_main.sh"

main "$@"
