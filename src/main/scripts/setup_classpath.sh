#!/usr/bin/env sh

CP=""

add_to_cp()
{
    if [ -n "$1" ]
    then
	add_cp=$(realpath -- "$1") || exit $?
	if [ ! -e "${add_cp}" ]
	then
	    echo "Warn: file not found: \`${add_cp}'" >&2
	fi
	CP="$CP${CP:+:}${add_cp}"
    fi
}

real_me=$(realpath -- "$0")
me_dir=$(dirname -- "${real_me}")

ifdefrevolver_dir=${me_dir}/../../..
skunk_dir=${ifdefrevolver_dir}/../Skunk/Skunk
reprodriller_dir=${ifdefrevolver_dir}/../repodriller
maven_repo=$HOME/.m2/repository

# IfdefRevolver class files
add_to_cp "${ifdefrevolver_dir}"/target/classes 

# Skunk class files
add_to_cp "${skunk_dir}"/target/classes

# commons-cli
add_to_cp "${maven_repo}"/commons-cli/commons-cli/1.3/commons-cli-1.3.jar

# open-csv
add_to_cp "${maven_repo}"/com/opencsv/opencsv/3.8/opencsv-3.8.jar

# apache CSV
add_to_cp "${maven_repo}"/org/apache/commons/commons-csv/1.4/commons-csv-1.4.jar

# apache IO
add_to_cp "${maven_repo}"/commons-io/commons-io/2.5/commons-io-2.5.jar

# repodriller classes
add_to_cp "${maven_repo}"/org/repodriller/repodriller/1.2.2-SNAPSHOT-no-git-branches/repodriller-1.2.2-SNAPSHOT-no-git-branches.jar

# egit (required by repodriller)
add_to_cp "${maven_repo}"/org/eclipse/jgit/org.eclipse.jgit/3.4.1.201406201815-r/org.eclipse.jgit-3.4.1.201406201815-r.jar

# repodriller requires google collections
add_to_cp "${maven_repo}"/com/google/guava/guava/18.0/guava-18.0.jar

# repodriller requires org/apache/commons/lang3/time/DateFormatUtils
add_to_cp "${maven_repo}"/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar

# log4j-api
#add_to_cp ${log4j_dir}/log4j-api-2.6.jar

# log4j-core
#add_to_cp ${log4j_dir}/log4j-core-2.6.jar

# log4j-1.2-api
#add_to_cp ${log4j_dir}/log4j-1.2-api-2.6.jar

# Log4j
add_to_cp "${maven_repo}"/log4j/log4j/1.2.14/log4j-1.2.14.jar
#add_to_cp "${maven_repo}"/org/apache/logging/log4j/log4j-api/2.1/log4j-api-2.1.jar
