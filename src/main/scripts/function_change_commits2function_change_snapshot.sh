#!/usr/bin/env sh

### Aggregate commit info (from function_change_commits.csv) for each
### function to the level of the snapshot.  The result is information
### about how much (in terms of number of commits, number of hunks,
### LOC delta, LOC deleted, LOC added) each function was changed
### within a snapshot.

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")

aggregate()
{
    csvsql -d, -q '"' -y 10000 --query \
	   "SELECT
		FUNCTION_SIGNATURE,
		FILE,
		MAX(FUNCTION_GROSS_LOC) as FUNCTION_GROSS_LOC,
		SUM(HUNKS) HUNKS,
		COUNT(COMMIT_ID) as COMMITS,
		SUM(BUGFIX) as BUGFIXES,
		SUM(LINE_DELTA) as LINE_DELTA,
		SUM(LINES_DELETED) as LINES_DELETED,
		SUM(LINES_ADDED) as LINES_ADDED
       	   FROM stdin
       	   GROUP BY FUNCTION_SIGNATURE,FILE"
}

AGG_INPUT_CSV=function_change_commits.csv
AGG_OUTPUT_CSV=function_change_snapshot.csv

. "${me_dir}/function_change_aggregate_main.sh"

main "$@"
