#!/usr/bin/env bash

colPrefix=ANY_SLOCMean
func=mean

#colPrefix=ANY_SLOCMedian
#func=median

cSmelly=${colPrefix}S
cNotSmelly=${colPrefix}NS

echo "System,$func $cNotSmelly,$func $cSmelly"
for sys in Apache BusyBox OpenLDAP OpenVPN Pidgin SQLite
do
    sysdir=$(echo "$sys"|tr '[[:upper:]]' '[[:lower:]]')
    f=results/$sysdir/corOverview.csv
    aNotSmelly=$( averages.R -c $cNotSmelly -f $func "$f" )
    aSmelly=$(    averages.R -c $cSmelly    -f $func "$f" )
    printf '%s,%.0f,%.0f\n' "$sys" $aNotSmelly $aSmelly
done
