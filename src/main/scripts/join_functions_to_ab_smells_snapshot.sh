#!/usr/bin/env sh

### Combine snapshot-level information about all functions and
### function changes with information about Annotation Bundle smells.
### The result is a complete picture about which functions were
### present at the start of the snapshot, which contained annotations
### or Annotation Bundle smells, which of them changed, and by how
### much, and how many times they were bugfixed.

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")

FUNCTIONS_NO_EXT=joint_function_change_snapshot
SMELLS_NO_EXT=ABRes

aggregate()
{
    ## Unprocessed header of the joint table
    ##
    ## SNAPSHOT_DATE,FUNCTION_SIGNATURE,FILE,FUNCTION_LOC,HUNKS,COMMITS,BUGFIXES,LINE_DELTA,LINES_DELETED,LINES_ADDED,FILE,Start,FUNCTION_SIGNATURE,ABSmell,LocationSmell,ConstantsSmell,NestingSmell,LOC,LOAC,LOFC,NOFL,NOFC_Dup,NOFC_NonDup,NONEST,NONEG
    
    csvsql -d, -q '"' -y 0 --query \
           "SELECT
                SNAPSHOT_DATE,
                f.FUNCTION_SIGNATURE,
                f.FILE,
                f.FUNCTION_LOC,
                HUNKS,
                COMMITS,
                BUGFIXES,
                LINES_CHANGED,
                LINE_DELTA,
                LINES_DELETED,
                LINES_ADDED,
		ABSmell,
		LocationSmell,
		ConstantsSmell,
		NestingSmell,
		LOAC,
		LOFC,
		NOFL,
		NOFC_Dup,
		NOFC_NonDup,
		NONEST,
		NONEG
            FROM
		functions f LEFT JOIN smells s
             	ON f.function_signature = s.function_signature
                   AND f.file = s.file" \
	   --tables functions,smells \
           "${1:?functions file missing}" "${2:?smells file missing}"
}

process_dirs()
{
    i_dir=1
    dirs_count=$#
    err=0
    err_dirs=""
    for dn_in in "$@"
    do
	fn_in_1="${dn_in}/${FUNCTIONS_NO_EXT:?}.csv"
	fn_in_2="${dn_in}/${SMELLS_NO_EXT:?}.csv"
	fn_out=${dn_in:?}/joint_function_ab_smell_snapshot.csv
	printf 'Processing directory %2d/%d: %s %s -> %s\n' ${i_dir} ${dirs_count} "${fn_in_1}" "${fn_in_2}" "${fn_out}" >&2
	tmp_out=$(mktemp "$fn_out".XXXXXXXX)
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
	    if [ -z "${err_dirs}" ]
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
