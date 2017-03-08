#!/usr/bin/env sh

### Aggregate commit info (from function_change_commits.csv) for each
### function to the level of the snapshot.  The result is information
### about how much (in terms of number of commits, number of hunks,
### LOC delta, LOC deleted, LOC added) each function was changed
### within a snapshot.

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")

ALL_FUNCTIONS_NO_EXT=all_functions
FUNCTION_CHANGE_SNAPSHOT_NO_EXT=function_change_snapshot

aggregate()
{
    csvsql -y 10000 --query \
           "SELECT
                SNAPSHOT_DATE,
                a.FUNCTION_SIGNATURE,
                a.FILE,
                a.FUNCTION_LOC      AS FUNCTION_LOC,
                IFNULL(HUNKS, 0)    AS HUNKS,
                IFNULL(COMMITS, 0)  AS COMMITS,
                IFNULL(BUGFIXES, 0) AS BUGFIXES,
                LINE_DELTA,
                LINES_DELETED,
                LINES_ADDED
            FROM
                     ${ALL_FUNCTIONS_NO_EXT:?} a
                LEFT JOIN
                     ${FUNCTION_CHANGE_SNAPSHOT_NO_EXT:?} c
                     ON
                        a.function_signature = c.function_signature
                        AND a.file = c.file" \
   "${1:?all_functions file missing}" "${2:?function_change_snapshot file missing}"
}

process_dirs()
{
    i_dir=1
    dirs_count=$#
    err=0
    err_dirs=""
    for dn_in in "$@"
    do
	fn_in_1="${dn_in}/${ALL_FUNCTIONS_NO_EXT:?}.csv"
	fn_in_2="${dn_in}/${FUNCTION_CHANGE_SNAPSHOT_NO_EXT:?}.csv"
	fn_out=${dn_in:?}/joint_function_change_snapshot.csv
	printf 'Processing directory %2d/%d: %s %s -> %s\n' ${i_dir} ${dirs_count} "${fn_in_1}" "${fn_in_2}" "${fn_out}" >&2
	tmp_out=$(mktemp joint_function_change_snapshot.csv.XXXXXXXX)
	dir_err=$?
	if [ $dir_err -ne 0 ]
	then
	    echo "Failed to create temporary file" >&2
	    unset tmp_out
	else
	    aggregate "${fn_in_1}" "${fn_in_2}" > "${tmp_out}"
	    dir_err=$?
	fi
	if [ $dir_err -eq 0 ]
	then
	    mv -f "${tmp_out}" "${fn_out}"
	    unset tmp_out
	else
	    echo "Error processing directory ${dn_in}" >&2
	    if [ -z "${err_files}" ]
	    then
		err_dirs="${dn_in}"
	    else
		err_dirs=$(printf '%s\n%s\n' "${err_dirs}" "${dn_in}")
	    fi
	    err=$(( $err + 1 ))
	fi
	i_dir=$(( $i_dir + 1 ))
	
	# Remove the temporary file if it still exists
	if [ -n "${tmp_out}" -a -e "${tmp_out}" ]
	then
	    rm -f "${tmp_out}"
	fi
    done
    if [ $err -ne 0 ]
    then
	echo "Errors occurred while processing the following directories:" >&2
	echo "${err_dirs}" >&2
	echo "($err errors)" >&2
    else
	echo "Successfully processed $dirs_count directories."
    fi
    return $err
}

main()
{
    if [ $# -eq 0 ]
    then
	process_dirs .
    else
	process_dirs "$@"
    fi
}

main "$@"
