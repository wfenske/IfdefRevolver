AB_SMELL_DEF = $(HOME)/src/skunk/trialrun/smellconfigs/AnnotationBundle.csm

ALL_PROJECTS_ROOT = ../../..

CUR_SNAPSHOT_RESULTS_DIR = $(shell realpath . )

CUR_SNAPSHOT_DATE = $(shell basename $(CUR_SNAPSHOT_RESULTS_DIR) )

PROJECT_DIR_OPTS = --resultsdir=$(ALL_PROJECTS_ROOT)/results --snapshotsdir=$(ALL_PROJECTS_ROOT)/snapshots

REPO_DIR = $(ALL_PROJECTS_ROOT)/repos/$(PROJECT)/.git

ALL_FUNCTIONS_DEPS =

CHANGED_FUNCTIONS_DEPS =

all: joint_function_ab_smell_snapshot.csv

joint_function_ab_smell_snapshot.csv: ABRes.csv joint_function_change_snapshot.csv
	join_functions_to_ab_smells_snapshot.sh .

joint_function_change_snapshot.csv: all_functions.csv function_change_snapshot.csv
	join_all_functions_to_function_change_snapshot.sh .

all_functions.csv: $(ALL_FUNCTIONS_DEPS)
	lsallfuncs.sh -p $(PROJECT) $(PROJECT_DIR_OPTS) $(CUR_SNAPSHOT_DATE)

function_change_snapshot.csv: function_change_commits.csv
	function_change_hunks2function_change_commits.sh $<

function_change_commits.csv: function_change_hunks.csv
	function_change_commits2function_change_snapshot.sh $<

function_change_hunks.csv: $(CHANGED_FUNCTIONS_DEPS)
	lschfuncs.sh -p $(PROJECT) --repo=$(REPO_DIR) $(PROJECT_DIR_OPTS) $(CUR_SNAPSHOT_DATE)

ABRes.csv: skunk_intermediate_functions.xml $(AB_SMELL_DEF)
	createsnapshots.sh -p $(PROJECT) $(PROJECT_DIR_OPTS) --smellconfigsdir=$(ALL_PROJECTS_ROOT)/smellconfigs --detect=AB $(CUR_SNAPSHOT_DATE)

skunk_intermediate_functions.xml:
	createsnapshots.sh -p $(PROJECT) $(PROJECT_DIR_OPTS) --preprocess $(CUR_SNAPSHOT_DATE)
