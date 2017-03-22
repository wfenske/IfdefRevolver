#!/usr/bin/env bash

resultsdir=${1:-results}

INDEPS=
#INDEPS+=" ABSmell"
INDEPS+=" NOFL"
INDEPS+=" NOFC_Dup"
#INDEPS+=" NONEST"

DEPS=
DEPS+=" BUGFIXES"
DEPS+=" HUNKS"
DEPS+=" COMMITS"

SCALES=
#SCALES+=" none"
SCALES+=" LOC"
#SCALES+=" COUNT"

SYSTEMS=
#SYSTEMS+=" Apache"
#SYSTEMS+=" BusyBox"
#SYSTEMS+=" OpenLDAP"
#SYSTEMS+=" OpenVPN"
#SYSTEMS+=" Pidgin"
SYSTEMS+=" SQLite"

err_combos=""
err=0

for indep in $INDEPS
do
    for dep in $DEPS
    do
	for scale in $SCALES
	do
	    case $scale in
		none) opt_ymax='';;
		*) opt_ymax="--ymax=0.5";;
	    esac
	    
	    for sys in $SYSTEMS
	    do
		# Only show Y-axis labels for some systems
		case $sys in
		    Apache|OpenVPN) opt_omit_y_axis=;;
		    *)              opt_omit_y_axis="-Y";;
		esac

		combo="$indep -> $dep/$scale in $sys"
		echo "Plotting $indep -> $dep/$scale in $sys"
		sysdir=$(echo "$sys"|tr '[[:upper:]]' '[[:lower:]]')
		ratioscmp.R $opt_ymax \
			    --independent=$indep \
			    --dependent=$dep \
			    --scale=$scale \
			    --systemname=$sys \
			    -s $resultsdir/$sysdir
		combo_err=$?
		if [ $combo_err -ne 0 ]
		then
		    echo $combo_err
		    echo "Error creating plot for combination $combo" >&2
		    if [ -z "$err_combos" ]
		    then
			err_combos="$combo"
		    else
			err_combos=$(printf '%s\n%s\n' "$err_combos" "$combo")
		    fi
		    err=$(( $err + 1 ))
		    break
		fi
	    done
	    test $combo_err -gt 3 && break
	done
	test $combo_err -gt 3 && break
    done
    test $combo_err -gt 3 && break
done

if [ $err -eq 0 ]
then
    echo "Successfully created all plots." >&2
else
    echo "$err error(s) occurred." >&2
    echo "Errors occurred plotting the following combinations:" >&2
    printf '%s\n' "$err_combos" >&2
    exit $err
fi

##printf " cropping ..."
##pdfcrop --margins 10 ${TMPFILE} "$output_file" >/dev/null 2>&1
