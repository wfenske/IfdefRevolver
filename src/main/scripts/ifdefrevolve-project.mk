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

REVISIONS_FILE = results/$(PROJECT)/revisionsFull.csv
CHECKOUT_MARKER = results/$(PROJECT)/.checkout_successful
ANALYSIS_MARKER = results/$(PROJECT)/.analysis_successful
ANALYZE_MAKEFILE = results/$(PROJECT)/analyze.mk

all: $(REVISIONS_FILE) $(CHECKOUT_MARKER) $(ANALYSIS_MARKER)

findrevisions: $(REVISIONS_FILE)

$(REVISIONS_FILE):
	@mkdir -p logs/$(PROJECT)
	@mkdir -p results/$(PROJECT)
	lscommits.sh -r repos/$(PROJECT) -o $(REVISIONS_FILE) >> logs/$(PROJECT)/lscommits.log 2>&1

checkout: $(CHECKOUT_MARKER)

$(CHECKOUT_MARKER): $(REVISIONS_FILE)
	@mkdir -p logs/$(PROJECT)
	@mkdir -p snapshots/$(PROJECT)
	rm -f $@
	createsnapshots.sh -p $(PROJECT) --checkout $(WINDOW_SIZE_OPT) >> logs/$(PROJECT)/checkout.log 2>&1 && \
	touch $@

analyze: $(ANALYSIS_MARKER)

$(ANALYSIS_MARKER): $(ANALYZE_MAKEFILE) $(CHECKOUT_MARKER)
	@mkdir -p logs/$(PROJECT)
	@mkdir -p snapshots/$(PROJECT)
	rm -f $(ANALYSIS_MARKER)
	make -f $< >> logs/$(PROJECT)/analyze.log 2>&1 && \
	touch $(ANALYSIS_MARKER)

$(ANALYZE_MAKEFILE): $(CHECKOUT_MARKER)
	configure-project.sh $(PROJECT) > $@ || ( rm -f $@; false )