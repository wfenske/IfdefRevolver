#!/bin/sh
for sys in apache busybox openldap openvpn pidgin sqlite
do
	fn="logit.${sys}.log"
	logit.R $sys/Correlated/*.csv > "$fn" && echo "$fn"
done
