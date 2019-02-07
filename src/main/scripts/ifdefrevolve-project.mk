### Makefile to run the entire analysis of a project, i.e., identifying
### relevant commits, creating snapshots, preprocessing and metrics
### detection. Only input is the project name (given via PROJECT
### environment variable), and the commit window size (given via
### WINDOW_SIZE environment variable).

### From the environment
#PROJECT ?= $(PROJECT)
WINDOW_SIZE_OPT =
ifdef WINDOW_SIZE
WINDOW_SIZE_OPT	= -s $(WINDOW_SIZE)
endif
###

##DRY_RUN ?= ""
##
##ifeq ($(DRY_RUN), "")
##DRY_RUN_CMD = touch
##else
##DRY_RUN_CMD = echo
##endif

RESULTS_DIR         = $(PROJECT)/results
SNAPSHOTS_DIR       = $(PROJECT)/snapshots
LOGS_DIR            = $(PROJECT)/logs

REVISIONS_FILE      = $(RESULTS_DIR)/revisionsFull.csv
COMMIT_PARENTS_FILE = $(RESULTS_DIR)/commitParents.csv
CHECKOUT_MARKER     = $(RESULTS_DIR)/.checkout_successful
ANALYSIS_MARKER     = $(RESULTS_DIR)/.analysis_successful
ANALYZE_MAKEFILE    = $(RESULTS_DIR)/analyze.mk

all: $(REVISIONS_FILE)  $(COMMIT_PARENTS_FILE) $(CHECKOUT_MARKER) $(ANALYSIS_MARKER)

findrevisions: $(REVISIONS_FILE) $(COMMIT_PARENTS_FILE)

$(REVISIONS_FILE):
	@mkdir -p $(LOGS_DIR)
	@mkdir -p $(RESULTS_DIR)
	lscommits.sh -r repos/$(PROJECT) -o $(REVISIONS_FILE) >> $(LOGS_DIR)/lscommits.log 2>&1

$(COMMIT_PARENTS_FILE):
	@mkdir -p $(LOGS_DIR)
	@mkdir -p $(RESULTS_DIR)
	lsparentcommits.sh -r repos/$(PROJECT) -o $(COMMIT_PARENTS_FILE) >> $(LOGS_DIR)/lsparentcommits.log 2>&1

checkout: $(CHECKOUT_MARKER)

$(CHECKOUT_MARKER): $(REVISIONS_FILE) $(COMMIT_PARENTS_FILE)
	@mkdir -p $(LOGS_DIR)
	@mkdir -p $(SNAPSHOTS_DIR)
	rm -f $@
	createsnapshots.sh -p $(PROJECT) --checkout $(WINDOW_SIZE_OPT) >> $(LOGS_DIR)/checkout.log 2>&1
	touch $@

analyze: $(ANALYSIS_MARKER)

$(ANALYSIS_MARKER): $(ANALYZE_MAKEFILE) $(CHECKOUT_MARKER)
	@mkdir -p $(LOGS_DIR)
	@mkdir -p $(SNAPSHOTS_DIR)
	rm -f $(ANALYSIS_MARKER)
	$(MAKE) -f $< >> $(LOGS_DIR)/analyze.log 2>&1
	touch $@

$(ANALYZE_MAKEFILE): $(CHECKOUT_MARKER)
	configure-project.sh $(PROJECT) > $@ || ( rm -f $@; false )
