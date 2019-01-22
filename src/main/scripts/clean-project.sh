#!/usr/bin/env sh
p=${1:?"Missing project name (first positional argument)"}
dir_me=$(dirname -- "$0")
##cd -- "$dir_me" || exit $?

# Enter results directory for project.
dir_old="$PWD"
if [ -d "$p"/results ]
then
    cd -- "$p/results" || exit $?
    if askyesno -y "Delete results in \`$PWD'?"
    then
        for f in *
	do
		if [ x"$f" != x"revisionsFull.csv" ]
		then
		    rm -rf -- "$f"
		fi
	done
	echo "Results deleted"
    else
	echo "Keeping results"
    fi
    cd -- "$dir_old" || exit $?
else
    echo "No results to delete for \`$p'"
fi

if [ -e "$p"/snapshots ]
then
    if askyesno -y "Delete snapshots in \`$PWD/$p/snapshots'?"
    then
	rm -rf "$p"/snapshots && \
	echo "Snapshots deleted"
    else
	echo "Keeping snapshots"
    fi
else
    echo "No snapshots to delete for \`$p'"
fi
