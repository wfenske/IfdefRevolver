#!/usr/bin/env sh
sys=openldap

# Determine the dates of the longest branch in the system
echo "# Fetching dates of longest branch of $sys..." >&2
dates=$(show-snapshot-branches.sh "$sys"|csvsql -d ',' -q '"' --table branches --query 'select date from branches where branch = (select branch from branches where position = (select max(position) from branches) limit 1)'|tail -n +2)

count_changed_ab_smells()
{
    echo "# Determining changed annotated functions of $sys ..." >&2
    last_date=$1
    shift
    o_header=--header
    for date in $@
    do
	echo "# $last_date -> $date" >&2
	count-changed-absmells.sh $o_header  $sys $last_date $date || break
	o_header=
	last_date=$date
    done
}

count_changed_ab_smells $dates
