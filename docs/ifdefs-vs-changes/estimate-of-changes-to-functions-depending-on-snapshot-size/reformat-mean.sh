#!/usr/bin/env sh
case $1 in
    *'- Mean:'*)
	num=$(printf '%s\n' "$1"|sed 's/[^0-9]*\([0-9]\{1,\}\.[0-9]\{1,\}\).*/\1/')
	printf '  - Mean: %.2f\n' "$num"
	;;
    *) printf '%s\n' "$1"
esac
