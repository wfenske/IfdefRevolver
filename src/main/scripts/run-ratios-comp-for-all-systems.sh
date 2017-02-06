#!/usr/bin/env bash
for suff in "" Size
do
	for sys in Apache BusyBox OpenLDAP OpenVPN Pidgin SQLite
	do
	    # Only show Y-axis labels for some systems
	    sysdir=$(echo "$sys"|tr '[[:upper:]]' '[[:lower:]]')
	    case $sysdir in
		apache|openvpn) opt_omit_y_axis=;;
		*)              opt_omit_y_axis="-Y";;
	    esac
	    output_file=ratios-cmp${suff}-$sysdir.pdf
	    printf "Generating %s ..." "$output_file" >&2
	    TMPFILE=$(mktemp ratios-cmp-XXXXXXXX) || exit $?
	    fsratioscmp.R $opt_omit_y_axis \
			    -o ${TMPFILE} \
			    --systemname="$sys" \
			    results/$sysdir/corOverview$suff.csv
	    err=$?
	    if [ $err -eq 0 ]
	    then
		printf " cropping ..."
		pdfcrop --margins 10 ${TMPFILE} "$output_file" >/dev/null 2>&1
		err=$?
		[ $err -ne 0 ] && echo " Cropping failed. Exiting." >&2
	    fi
	    rm -f $TMPFILE
	    [ $err -ne 0 ] && exit $err
	    echo " done." >&2
	done
done
