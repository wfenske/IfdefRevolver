### Main loop of the function_change_<XY>2function_change_<YZ>.sh scripts

main()
{
    i_file=1
    files_count=$#
    err=0
    err_files=""
    for dn_in in "$@"
    do
	dn_in=${dn_in%/}
	fn_in=${dn_in}/${AGG_INPUT_CSV:?}
	fn_out=${dn_in}/${AGG_OUTPUT_CSV:?}
	printf 'Processing file %2d/%d: %s -> %s\n' ${i_file} ${files_count} "${fn_in}" "${fn_out}" >&2
	if [ ! -e "${fn_in}" ]
	then
	    echo "Input does not exit: \`${fn_in}'" >&2
	    false
	elif [ ! -s "${fn_in}" ]
	then
	    echo "Input file is empty: \`${fn_in}'" >&2
	    false
	else
	    fn_out_tmp=$(mktemp -- "$fn_out".XXXXXXXX)
	    if [ $? -eq 0 ]
	    then
		cat -- "${fn_in}" | aggregate > "${fn_out_tmp}"
		if [ $? -eq 0 ]
		then
		    cat -- "${fn_out_tmp}" > "${fn_out}"
		    rm -f "${fn_out_tmp}"
		else
		    rm -f "${fn_out_tmp}"
		    false
		fi
	    else
		echo "Error creating temporary file." >&2
		false
	    fi
	fi
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
	i_file=$(( $i_file + 1 ))
    done
    if [ $err -ne 0 ]
    then
	echo "Errors occurred while processing the following files:" >&2
	echo "${err_files}" >&2
	echo "($err errors)" >&2
    else
	echo "Successfully processed $files_count file(s)."
    fi
    return $err
}
