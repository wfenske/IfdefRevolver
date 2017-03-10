#!/usr/bin/env bash
echo "# All files" >&2
( echo "(/ (+ "; grep '^ANY;' fisher-all.csv|cut -f3 -d';'; echo ") 6.0)" )|flb -r|sed 's/\./,/'
echo "# Large file values" >&2
( echo "(/ (+ "; grep '^ANY;' fisher-allSize.csv|cut -f3 -d';'; echo ") 6.0)" )|flb -r|sed 's/\./,/'
