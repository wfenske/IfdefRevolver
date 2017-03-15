#/usr/bin/env sh

#set -x

me=$(realpath -- "$0")
me_dir=$(dirname -- "$me")

SNAPSHOT_MK_IN="$me_dir/snapshot-results.mk.in"

PROJECT=${1:?project name missing}

SMELL_CONFIGS_DIR="smellconfigs"

PROJECT_RESULTS_DIR="results/${PROJECT:?}"

TOP_LEVEL_SNAPSHOT_RESULT_FILE=joint_function_ab_smell_snapshot.csv

if [ ! -d "$PROJECT_RESULTS_DIR" ]
then
    echo "Project results directory does not exist or is not a directory: \`$PROJECT_RESULTS_DIR'" >&2
    exit 1
fi

all_snapshot_rules=""
all_deps=""

have_snapshot_results_dir=false
for snapshot_results_dir in "${PROJECT_RESULTS_DIR:?}"/[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]
do
    if [ ! -d "$snapshot_results_dir" ]
    then
	echo "Not a directory: $snapshot_results_dir" >&2
	continue
    fi
    have_snapshot_results_dir=true
    snapshot_date=$(basename -- "$snapshot_results_dir") || exit $?
    snapshot_rules=$(sed \
			 -e "s|%%SNAPSHOT_RESULTS_DIR%%|$snapshot_results_dir|g" \
			 -e "s|%%SNAPSHOT_DATE%%|$snapshot_date|g" \
			 -- \
			 "${SNAPSHOT_MK_IN:?}" ) || exit $?
    snapshot_rules=$(printf '%s\n' "$snapshot_rules" | grep -v '^[[:space:]]*#')
    all_snapshot_rules=$(printf '%s\n\n%s\n' "$all_snapshot_rules" "$snapshot_rules")
    all_deps=$(printf '%s %s/%s' "$all_deps" "$snapshot_results_dir" "${TOP_LEVEL_SNAPSHOT_RESULT_FILE:?}")
done

if ! $have_snapshot_results_dir
then
    echo "No snapshot results directories found in \`$PROJECT_RESULTS_DIR'" >&2
    exit 1
fi

printf 'PROJECT = %s\nSMELL_CONFIGS_DIR = %s\n\n### Top rule\nall: %s\n\n### Rules for individual snapshots\n%s\n' \
       "${PROJECT:?}" \
       "${SMELL_CONFIGS_DIR}" \
       "${all_deps}" \
       "${all_snapshot_rules:?}"
