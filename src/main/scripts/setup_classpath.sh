#!/usr/bin/env sh

CP=""

add_to_cp()
{
    if [ -n "$1" ]
    then
	if [ ! -e "$1" ]
	then
	    echo "Warn: file not found: \`$1'" >&2
	fi
	CP="$CP${CP:+:}${1}"
    fi
}

ifdefrevolver_dir=$(dirname "$0")/../../..
skunk_dir=${ifdefrevolver_dir}/../Skunk/Skunk
reprodriller_dir=${ifdefrevolver_dir}/../repodriller
maven_repo=$HOME/.m2/repository
log4j_dir="${reprodriller_dir}"/lib/apache-log4j-2.6-bin

# Skunk class files
add_to_cp "${skunk_dir}"/bin

# IfdefRevolver class files
add_to_cp "${ifdefrevolver_dir}"/target/classes 

# commons-cli
add_to_cp "${maven_repo}"/commons-cli/commons-cli/1.3/commons-cli-1.3.jar

# open-csv
add_to_cp "${maven_repo}"/com/opencsv/opencsv/3.8/opencsv-3.8.jar

# repodriller classes
add_to_cp "${maven_repo}"/org/repodriller/repodriller/1.2.2-SNAPSHOT-no-git-branches/repodriller-1.2.2-SNAPSHOT-no-git-branches.jar

# log4j-api
add_to_cp ${log4j_dir}/log4j-api-2.6.jar

# log4j-core
add_to_cp ${log4j_dir}/log4j-core-2.6.jar

# log4j-1.2-api
add_to_cp ${log4j_dir}/log4j-1.2-api-2.6.jar
