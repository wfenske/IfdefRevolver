#/usr/bin/env sh

#set -x

me=$(realpath -- "$0")
me_dir=$(dirname -- "$me")

SNAPSHOT_MK_IN="$me_dir/snapshot-results.mk.in"
LEFTOVER_SNAPSHOT_MK_IN="$me_dir/leftover-snapshot-results.mk.in"

if [ $# -ne 1 ]
then
    echo "usage: $me PROJECT_NAME" >&2
    exit 1
fi

PROJECT=${1:?project name missing}

SMELL_CONFIGS_DIR="smellconfigs"

PROJECT_RESULTS_DIR="results/${PROJECT:?}"

TOP_LEVEL_SNAPSHOT_RESULT_FILE="${PROJECT_RESULTS_DIR:?}/joint_function_ab_smell_age_snapshot.csv"

if [ ! -d "$PROJECT_RESULTS_DIR" ]
then
    echo "Project results directory does not exist or is not a directory: \`$PROJECT_RESULTS_DIR'" >&2
    exit 1
fi

all_snapshot_rules=""
joint_deps=""

have_snapshot_results_dir=false
snapshot_dates=$(cut -f 2 -d ',' "${PROJECT_RESULTS_DIR}"/snapshots.csv) || exit $?
snapshot_dates=$(printf '%s\n' "$snapshot_dates"|tail -n +2) || exit $?
for snapshot_date in $snapshot_dates
do
    snapshot_results_dir="${PROJECT_RESULTS_DIR:?}"/$snapshot_date
    
    if [ ! -d "$snapshot_results_dir" ]
    then
	echo "Not a directory: $snapshot_results_dir" >&2
	continue
    fi
    
    have_snapshot_results_dir=true
    
    snapshot_rules=$(sed \
			 -e "s|%%PROJECT_RESULTS_DIR%%|${PROJECT_RESULTS_DIR:?}|g" \
			 -e "s|%%SNAPSHOT_DATE%%|$snapshot_date|g" \
			 -- \
			 "${SNAPSHOT_MK_IN:?}" ) || exit $?
    
    snapshot_rules=$(printf '%s\n' "$snapshot_rules" | grep -v '^[[:space:]]*#')
    all_snapshot_rules=$(printf '%s\n\n%s\n' "$all_snapshot_rules" "$snapshot_rules")
    joint_deps="$joint_deps $snapshot_results_dir/all_functions.csv $snapshot_results_dir/function_change_hunks.csv $snapshot_results_dir/ABRes.csv"
done

if ! $have_snapshot_results_dir
then
    echo "WARN: No snapshot results directories found in \`$PROJECT_RESULTS_DIR'" >&2
    #exit 1
fi

joint_deps="$joint_deps ${PROJECT_RESULTS_DIR:?}/1970-01-01/function_change_hunks.csv"

leftover_snapshot_rules=$(sed \
		       -e "s|%%PROJECT_RESULTS_DIR%%|${PROJECT_RESULTS_DIR:?}|g" \
		       -- \
		       "${LEFTOVER_SNAPSHOT_MK_IN:?}" ) || exit $?

printf 'PROJECT = %s\nSMELL_CONFIGS_DIR = %s\n\n### Top rule\nall: %s\n\n%s: %s\n\taddchangedistances.sh -p $(PROJECT)\n\n### Changes of leftover snapshot: %s\n\n### Rules for individual snapshots\n%s\n' \
       "${PROJECT:?}" \
       "${SMELL_CONFIGS_DIR}" \
       "${TOP_LEVEL_SNAPSHOT_RESULT_FILE:?}" \
       "${TOP_LEVEL_SNAPSHOT_RESULT_FILE:?}" \
       "${joint_deps}" \
       "${leftover_snapshot_rules}" \
       "${all_snapshot_rules}"
