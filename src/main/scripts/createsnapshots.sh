#!/usr/bin/env sh

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")
. "${me_dir}"/setup_classpath.sh || exit $?

o_smellconfigs_dir="--smellconfigsdir=${me_dir}/../resources/smellconfigs"
o_jvm="-Xmx256m"

for o in "$@"; do
    case "$o" in
	--smellconfigsdir*)
	    unset o_smellconfigs_dir
	    break
	    ;;
    esac
done

main_class=de.ovgu.skunk.bugs.createsnapshots.main.CreateSnapshots
if [ -n "$o_smellconfigs_dir" ]
then
    exec java ${o_jvm} -cp "$CP" ${main_class} $o_smellconfigs_dir "$@"
else
    exec java ${o_jvm} -cp "$CP" ${main_class}                     "$@"
fi
