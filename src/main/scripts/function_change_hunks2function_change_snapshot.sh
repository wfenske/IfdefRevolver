#!/usr/bin/env sh

### Aggregate commit hunk info for each function to the level of the
### snapshot.  The result is information about how much (in terms of
### number of commits, number of hunks, LOC delta, LOC deleted, LOC
### added) each function was changed within a snapshot.

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")

aggregate()
{
    csvsql -y 10000 --query \
	   "SELECT FUNCTION_SIGNATURE,FILE,MAX(FUNCTION_LOC) as FUNCTION_LOC,COUNT(COMMIT_ID) HUNKS,COUNT(DISTINCT COMMIT_ID) as COMMITS,SUM(LINE_DELTA) as LINE_DELTA,SUM(LINES_DELETED) as LINES_DELETED,SUM(LINES_ADDED) as LINES_ADDED
       	   FROM stdin
       	   GROUP BY FUNCTION_SIGNATURE,FILE"
}

main()
{
    if [ $# -eq 0 ]
    then
	aggregate
    elif [ $# -eq 1 ]
    then
	cat -- "$1"|aggregate
    else
	i_file=1
	files_count=$#
	err=0
	err_files=""
	for fn_in in "$@"
	do
	    fn_out=$(dirname -- "$fn_in")/function_change_snapshot.csv
	    printf 'Processing file %2d/%d: %s -> %s\n' ${i_file} ${files_count} "${fn_in}" "${fn_out}" >&2
	    cat -- "$fn_in" | aggregate > "${fn_out}"
	    if [ $? -ne 0 ]
	    then
		echo "Error processing file ${fn_in}" >&2
		if [ -z "${err_files}" ]
		then
		    err_files="${fn_in}"
		else
		    err_files=$(printf '%s\n%s\n' "${err_files}" "${fn_in}")
		fi
		err=$(( $err + 1 ))
	    fi
	    i_file=$(( ++i_file ))
	done
	if [ $err -ne 0 ]
	then
	    echo "Errors occurred while processing the following files:" >&2
	    echo "${err_files}" >&2
	    echo "($err errors)" >&2
	else
	    echo "Successfully processed $files_count files."
	fi
	return $err
    fi
}

main "$@"
