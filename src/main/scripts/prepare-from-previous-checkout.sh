#!/usr/bin/env sh
export LANG=C
export LC_ALL=C

unset SIM
#SIM=echo

srcPath="${1:?source directory missing}"
dstPath="${2:?target directory missing}"

me=$(realpath -- "${0}") || exit $?
medir=$(dirname -- "$me") || exit $?

if [ -z "$LINK_CMD" ]
then
    LINK_CMD=$(command -v link_or_copy_file.py 2>/dev/null)
fi

if [ -z "$LINK_CMD" ]
then
    LINK_CMD=$(command -v ${medir}/../cppstats/link_or_copy_file.py 2>/dev/null)
fi

if [ -z "$LINK_CMD" ]
then
   echo "Cannot find utility script \`link_or_copy_file.py'" >&2
   exit 1
fi

diff -uqsr -- "$srcPath"/source "$dstPath"/source \
    | grep ' are identical$' \
    | sed 's,^Files \([^[:space:]]*\) and .*,\1,' \
    | while read srcFile
do
    ## line will look like this:
    ## Files 2010-03-03/source/os/bs2000/ebcdic.c and 2010-06-08/source/os/bs2000/ebcdic.c are identical
    relName=${srcFile#$srcPath/source/} || exit $?
    relDir=$(dirname "$relName") || exit $?
    for mode in _cppstats _cppstats_featurelocations
    do
	$SIM mkdir -p "$dstPath/$mode/$relDir/" || exit $?
	for fn in "${relName}" "${relName}".xml
	do
	    test -e "$srcPath/$mode/$fn" || continue
	    $SIM link_or_copy_file.py -f "$srcPath/$mode/$fn" "$dstPath/$mode/$fn" || exit $?
	done
    done
done
