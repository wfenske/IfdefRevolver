#!/usr/bin/env bash
for suff in "" Size
do
	for sys in apache busybox openldap openvpn pidgin sqlite
	do
		if [ $sys = "apache" ]; then nh=; else nh='-H'; fi
		fisher-all.sh $nh results/$sys/corOverview$suff.csv
	done  > fisher-all$suff.csv 2>&1
done
