#!/usr/bin/env sh
for smell in ANY \
		 AB AF LF \
		 ABorAF ABandAF \
		 ABorLF ABandLF \
		 AForLF AFandLF \
		 ANY2 \
		 ANY2Distinct \
		 AB2 LF2 \
		 ;
do
    if [ $smell = "ANY" ]
    then
	H=''
    else
	H="-H"
    fi
    fisher.R $H -s $smell $@ || exit $?
done
