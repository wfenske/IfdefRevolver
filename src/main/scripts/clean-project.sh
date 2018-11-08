#!/usr/bin/env sh
p=${1:?"Missing project name (first positional argument)"}
dir_me=$(dirname -- "$0")
##cd -- "$dir_me" || exit $?

# Enter results directory for project.
dir_old="$PWD"
if [ -d results/"$p" ]
then
    cd -- "results/$p" || exit $?
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

if [ -e snapshots/"$p" ]
then
    if askyesno -y "Delete snapshots in \`$PWD/snapshots/$p'?"
    then
	rm -rf snapshots/"$p" && \
	echo "Snapshots deleted"
    else
	echo "Keeping snapshots"
    fi
else
    echo "No snapshots to delete for \`$p'"
fi
