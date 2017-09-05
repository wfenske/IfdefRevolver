### Makefile to run statistics for a project

### From the environment
#PROJECT ?= $(PROJECT)
###

NAME ?= $(PROJECT)

FISHER_CSV = results/$(PROJECT)/fisher.csv
SPEARMAN_CSV = results/$(PROJECT)/spearman.csv
ALL_R_DATA = results/$(PROJECT)/allData.rdata

SKUNK_HOME ?= $(HOME)/src/skunk/IfdefRevolver/src/main/scripts

COMPARE_LOCS_PROG = $(SKUNK_HOME)/compare-locs.R
RATIOSCMP_PROG = $(SKUNK_HOME)/ratioscmp.R
FISHER_PROG = $(SKUNK_HOME)/fisher.R
SPEARMAN_PROG = $(SKUNK_HOME)/spearman.R
REGRESSION_PROG = $(SKUNK_HOME)/lm.R

COMPARE_LOC_OPTS ?= -d 3 --ymax 400

INPUT_CSVS = $(wildcard results/$(PROJECT)/[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/joint_function_ab_smell_snapshot.csv)

INDEPS = FL FC ND NEG
RATIOS_PLOTS = \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -COMMITS.COUNT.pdf,$(INDEPS))) \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -COMMITS.LOC.pdf,$(INDEPS)))   \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -LCH.COUNT.pdf,$(INDEPS)))     \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -LCH.LOC.pdf,$(INDEPS)))

LOC_PLOTS = $(addprefix loc-plots/$(PROJECT)/LOC-,$(addsuffix .pdf,$(INDEPS)))

REGRESSION_FULL_CSV = results/$(PROJECT)/regression-full.csv
REGRESSION_FULL_LOG = results/$(PROJECT)/regression-full.log

REGRESSION_CHANGED_CSV = results/$(PROJECT)/regression-changed.csv
REGRESSION_CHANGED_LOG = results/$(PROJECT)/regression-changed.log

REGRESSION_ANNOTATED_CSV = results/$(PROJECT)/regression-annotated.csv
REGRESSION_ANNOTATED_LOG = results/$(PROJECT)/regression-annotated.log

REGRESSION_ANNOTATED_CHANGED_CSV = results/$(PROJECT)/regression-annotated-changed.csv
REGRESSION_ANNOTATED_CHANGED_LOG = results/$(PROJECT)/regression-annotated-changed.log

WINDOW_EXCLUSION_MARKER = results/$(PROJECT)/.last_window_exclusion

all: fisher ratiosplots locplots spearman regressionmodels

fisher: $(FISHER_CSV)

spearman: $(SPEARMAN_CSV)

ratiosplots: $(RATIOS_PLOTS)

locplots: $(LOC_PLOTS)

regressionmodels: $(REGRESSION_FULL_CSV) $(REGRESSION_CHANGED_CSV) $(REGRESSION_ANNOTATED_CSV) $(REGRESSION_ANNOTATED_CHANGED_CSV)

$(FISHER_CSV): $(ALL_R_DATA) $(FISHER_PROG)
	if ! $(FISHER_PROG) -p $(PROJECT) > $(FISHER_CSV); \
	then \
		rm -f $(FISHER_CSV); \
		false; \
	fi

$(SPEARMAN_CSV): $(ALL_R_DATA) $(SPEARMAN_PROG)
	if !  $(SPEARMAN_PROG) -p $(PROJECT) > $(SPEARMAN_CSV); \
	then \
		rm -f $(SPEARMAN_CSV); \
		false; \
	fi

$(REGRESSION_FULL_CSV): $(ALL_R_DATA) $(REGRESSION_PROG)
	if ! $(REGRESSION_PROG) -p $(PROJECT) > $(REGRESSION_FULL_CSV) 2> $(REGRESSION_FULL_LOG); \
	then \
		rm -f $(REGRESSION_FULL_CSV); \
		false; \
	fi

$(REGRESSION_CHANGED_CSV): $(ALL_R_DATA) $(REGRESSION_PROG)
	if ! $(REGRESSION_PROG) -p $(PROJECT) --changed > $(REGRESSION_CHANGED_CSV) 2> $(REGRESSION_CHANGED_LOG); \
	then \
		rm -f $(REGRESSION_CHANGED_CSV); \
		false; \
	fi

$(REGRESSION_ANNOTATED_CSV): $(ALL_R_DATA) $(REGRESSION_PROG)
	if ! $(REGRESSION_PROG) -p $(PROJECT) --annotated > $(REGRESSION_ANNOTATED_CSV) 2> $(REGRESSION_ANNOTATED_LOG); \
	then \
		rm -f $(REGRESSION_ANNOTATED_CSV); \
		false; \
	fi

$(REGRESSION_ANNOTATED_CHANGED_CSV): $(ALL_R_DATA) $(REGRESSION_PROG)
	if ! $(REGRESSION_PROG) -p $(PROJECT) --annotated --changed > $(REGRESSION_ANNOTATED_CHANGED_CSV) 2> $(REGRESSION_ANNOTATED_CHANGED_LOG); \
	then \
		rm -f $(REGRESSION_ANNOTATED_CHANGED_CSV); \
		false; \
	fi

$(ALL_R_DATA): $(INPUT_CSVS) $(WINDOW_EXCLUSION_MARKER)
	rdata-from-csv.R -p $(PROJECT)

## INDEPS vs. COMMITS

ratios-plots/$(PROJECT)/ratios-FL-COMMITS.COUNT.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FL -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FC-COMMITS.COUNT.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FC -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-ND-COMMITS.COUNT.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i ND -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-NEG-COMMITS.COUNT.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i NEG -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FL-COMMITS.LOC.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FL -d COMMITS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-FC-COMMITS.LOC.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FC -d COMMITS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-ND-COMMITS.LOC.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i ND -d COMMITS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-NEG-COMMITS.LOC.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i NEG -d COMMITS -s LOC -o $@

## INDEPS vs. LCH

ratios-plots/$(PROJECT)/ratios-FL-LCH.COUNT.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FL -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FC-LCH.COUNT.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FC -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-ND-LCH.COUNT.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i ND -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-NEG-LCH.COUNT.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i NEG -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FL-LCH.LOC.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FL -d LCH -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-FC-LCH.LOC.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FC -d LCH -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-ND-LCH.LOC.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i ND -d LCH -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-NEG-LCH.LOC.pdf: $(ALL_R_DATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i NEG -d LCH -s LOC -o $@

## LOC plots
loc-plots/$(PROJECT)/LOC-FC.pdf: $(ALL_R_DATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -n $(NAME) -i FC $(COMPARE_LOC_OPTS) -o $@ -X --no-title

loc-plots/$(PROJECT)/LOC-FL.pdf: $(ALL_R_DATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -n $(NAME) -i FL $(COMPARE_LOC_OPTS) -o $@ -X --no-title

loc-plots/$(PROJECT)/LOC-ND.pdf: $(ALL_R_DATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -n $(NAME) -i CND $(COMPARE_LOC_OPTS) -o $@ -X --no-title

loc-plots/$(PROJECT)/LOC-NEG.pdf: $(ALL_R_DATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -n $(NAME) -i NEG $(COMPARE_LOC_OPTS) -o $@ --no-title
