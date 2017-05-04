#!/usr/bin/env bash

resultsdir=${1:-results}

INDEPS=
#INDEPS+=" ABSmell"
#INDEPS+=" NOFL"
#INDEPS+=" NOFC_Dup"
INDEPS+=" NOFC_NonDup"
#INDEPS+=" NONEST"

DEPS=
#DEPS+=" BUGFIXES"
#DEPS+=" HUNKS"
#DEPS+=" COMMITS"
#DEPS+=" LINE_DELTA"
#DEPS+=" LINES_ADDED"
#DEPS+=" LINES_DELETED"
DEPS+=" LINES_CHANGED"

SCALES=
#SCALES+=" none"
#SCALES+=" LOC"
SCALES+=" COUNT"

SYSTEMS=
#SYSTEMS+=" Apache"
#SYSTEMS+=" BusyBox"
SYSTEMS+=" OpenLDAP"
#SYSTEMS+=" OpenVPN"
#SYSTEMS+=" Pidgin"
#SYSTEMS+=" SQLite"

err_combos=""
combo_err=0

for indep in $INDEPS
do
    for dep in $DEPS
    do
	for scale in $SCALES
	do
###	    case $scale in
###		##LOC|COUNT) opt_ymax="--ymax=1.0";;
###		*) opt_ymax='';;
###	    esac
	    case ${dep}/${scale} in
		HUNKS/LOC) opt_ymax="--ymax=0.2";;
		LINES_CHANGED/LOC) opt_ymax="--ymax=0.5";;
		LINES_CHANGED/COUNT) opt_ymax="--ymax=50";;
		*) opt_ymax='';;
	    esac
	    
	    for sys in $SYSTEMS
	    do
###		# Only show Y-axis labels for some systems
###		case $sys in
###		    Apache|OpenVPN) ##opt_omit_y_axis=
###				    ;;
###		    *)              ##opt_omit_y_axis="-Y"
###				    ;;
###		esac

		combo="$indep -> $dep/$scale in $sys"
		echo "Plotting $indep -> $dep/$scale in $sys"
		sysdir=$(echo "$sys"|tr '[[:upper:]]' '[[:lower:]]')
		ratioscmp.R --independent=$indep \
			    --dependent=$dep \
			    --scale=$scale \
			    $opt_ymax \
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
		    combo_err=$(( $combo_err + 1 ))
		    break
		fi
	    done
	    test $combo_err -gt 3 && break
	    pdfjam-slides6up -o summary-ratios-${indep}-${dep}.${scale}.pdf \
			     --landscape \
			     ratios-${indep}-${dep}.${scale}-*.pdf
	done
	test $combo_err -gt 3 && break
    done
    test $combo_err -gt 3 && break
done

if [ $combo_err -eq 0 ]
then
    echo "Successfully created all plots." >&2
else
    echo "$combo_err error(s) occurred." >&2
    echo "Errors occurred plotting the following combinations:" >&2
    printf '%s\n' "$err_combos" >&2
    exit $combo_err
fi

##printf " cropping ..."
##pdfcrop --margins 10 ${TMPFILE} "$output_file" >/dev/null 2>&1
