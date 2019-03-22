### Makefile to run statistics for a project

### From the environment
#PROJECT ?= $(PROJECT)
###

NAME ?= $(PROJECT)

RESULTS_DIR  = $(PROJECT)/results
LOG_DIR      = $(PROJECT)/logs

FISHER_CSV   = $(RESULTS_DIR)/fisher.csv
SPEARMAN_CSV = $(RESULTS_DIR)/spearman.csv
RDATA        = $(RESULTS_DIR)/joint_data.rds

IFDEFREVOLVER_HOME ?= $(HOME)/src/skunk/IfdefRevolver/src/main/scripts

COMPARE_LOCS_PROG = $(IFDEFREVOLVER_HOME)/compare-locs.R
RATIOSCMP_PROG    = $(IFDEFREVOLVER_HOME)/ratioscmp.R
FISHER_PROG       = $(IFDEFREVOLVER_HOME)/fisher.R
SPEARMAN_PROG     = $(IFDEFREVOLVER_HOME)/spearman.R
NBREG_PROG        = $(IFDEFREVOLVER_HOME)/nb.R
LOGITREG_PROG     = $(IFDEFREVOLVER_HOME)/logit.R
RDS_FROM_CSV_PROG = $(IFDEFREVOLVER_HOME)/rds-from-csv.R

COMPARE_LOC_OPTS ?= -d 3 --ymax 400
RDS_FROM_CSV_OPTS ?=

INPUT_CSV = $(RESULTS_DIR)/joint_data.csv

INDEPS = FL FC CND NEG
RATIOS_PLOTS = \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -COMMITS.COUNT.pdf,$(INDEPS))) \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -COMMITS.LOC.pdf,$(INDEPS)))   \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -LCH.COUNT.pdf,$(INDEPS)))     \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -LCH.LOC.pdf,$(INDEPS)))

LOC_PLOTS = $(addprefix loc-plots/$(PROJECT)/LOC-,$(addsuffix .pdf,$(INDEPS)))

NBREG_FULL_CSV                 = $(RESULTS_DIR)/nb-reg.csv
NBREG_FULL_LOG                 = $(LOG_DIR)/nb-reg.log

##NBREG_CHANGED_CSV              = $(RESULTS_DIR)/nb-reg-changed.csv
##NBREG_CHANGED_LOG              = $(LOG_DIR)/nb-reg-changed.log
##
##NBREG_ANNOTATED_CSV            = $(RESULTS_DIR)/nb-reg-annotated.csv
##NBREG_ANNOTATED_LOG            = $(LOG_DIR)/nb-reg-annotated.log
##
##NBREG_ANNOTATED_CHANGED_CSV    = $(RESULTS_DIR)/nb-reg-annotated-changed.csv
##NBREG_ANNOTATED_CHANGED_LOG    = $(LOG_DIR)/nb-reg-annotated-changed.log

LOGITREG_FULL_CSV              = $(RESULTS_DIR)/logit-reg.csv
LOGITREG_FULL_LOG              = $(LOG_DIR)/logit-reg.log

REGRESSIONMODELS = $(NBREG_FULL_CSV) $(LOGITREG_FULL_CSV)
## $(NBREG_CHANGED_CSV) $(NBREG_ANNOTATED_CSV) $(NBREG_ANNOTATED_CHANGED_CSV) 

all: fisher ratiosplots locplots spearman regressionmodels

fisher: $(FISHER_CSV)

spearman: $(SPEARMAN_CSV)

ratiosplots: $(RATIOS_PLOTS)

locplots: $(LOC_PLOTS)

regressionmodels: $(REGRESSIONMODELS)

$(FISHER_CSV): $(RDATA) $(FISHER_PROG)
	if ! $(FISHER_PROG) -p $(PROJECT) > $(FISHER_CSV); \
	then \
		rm -f $(FISHER_CSV); \
		false; \
	fi

$(SPEARMAN_CSV): $(RDATA) $(SPEARMAN_PROG)
	if !  $(SPEARMAN_PROG) -p $(PROJECT) > $(SPEARMAN_CSV); \
	then \
		rm -f $(SPEARMAN_CSV); \
		false; \
	fi

$(NBREG_FULL_CSV): $(RDATA) $(NBREG_PROG)
	if ! $(NBREG_PROG) -p $(PROJECT) 2>&1 > $(NBREG_FULL_CSV)|tee $(NBREG_FULL_LOG) >&2; \
	then \
		rm -f $(NBREG_FULL_CSV); \
		false; \
	fi

##$(NBREG_CHANGED_CSV): $(RDATA) $(NBREG_PROG)
##	if ! $(NBREG_PROG) -p $(PROJECT) --changed > $(NBREG_CHANGED_CSV) 2> $(NBREG_CHANGED_LOG); \
##	then \
##		rm -f $(NBREG_CHANGED_CSV); \
##		false; \
##	fi
##
##$(NBREG_ANNOTATED_CSV): $(RDATA) $(NBREG_PROG)
##	if ! $(NBREG_PROG) -p $(PROJECT) --annotated > $(NBREG_ANNOTATED_CSV) 2> $(NBREG_ANNOTATED_LOG); \
##	then \
##		rm -f $(NBREG_ANNOTATED_CSV); \
##		false; \
##	fi
##
##$(NBREG_ANNOTATED_CHANGED_CSV): $(RDATA) $(NBREG_PROG)
##	if ! $(NBREG_PROG) -p $(PROJECT) --annotated --changed > $(NBREG_ANNOTATED_CHANGED_CSV) 2> $(NBREG_ANNOTATED_CHANGED_LOG); \
##	then \
##		rm -f $(NBREG_ANNOTATED_CHANGED_CSV); \
##		false; \
##	fi

$(LOGITREG_FULL_CSV): $(RDATA) $(LOGITREG_PROG)
	if ! $(LOGITREG_PROG) -p $(PROJECT) 2>&1 > $(LOGITREG_FULL_CSV)|tee $(LOGITREG_FULL_LOG) >&2; \
	then \
		rm -f $(LOGITREG_FULL_CSV); \
		false; \
	fi

$(RDATA): $(INPUT_CSV) $(RDS_FROM_CSV_PROG)
	$(RDS_FROM_CSV_PROG) -p $(PROJECT) $(RDS_FROM_CSV_OPTS) 2>&1|tee $(LOG_DIR)/rds.log >&2

## INDEPS vs. COMMITS

ratios-plots/$(PROJECT)/ratios-FL-COMMITS.COUNT.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FL -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FC-COMMITS.COUNT.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FC -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-ND-COMMITS.COUNT.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i ND -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-NEG-COMMITS.COUNT.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i NEG -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FL-COMMITS.LOC.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FL -d COMMITS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-FC-COMMITS.LOC.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FC -d COMMITS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-ND-COMMITS.LOC.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i ND -d COMMITS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-NEG-COMMITS.LOC.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i NEG -d COMMITS -s LOC -o $@

## INDEPS vs. LCH

ratios-plots/$(PROJECT)/ratios-FL-LCH.COUNT.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FL -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FC-LCH.COUNT.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FC -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-ND-LCH.COUNT.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i ND -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-NEG-LCH.COUNT.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i NEG -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FL-LCH.LOC.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FL -d LCH -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-FC-LCH.LOC.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i FC -d LCH -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-ND-LCH.LOC.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i ND -d LCH -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-NEG-LCH.LOC.pdf: $(RDATA) $(RATIOSCMP_PROG)
	mkdir -p `dirname $@` 
	$(RATIOSCMP_PROG) -p $(PROJECT) -n $(NAME) -i NEG -d LCH -s LOC -o $@

## LOC plots
loc-plots/$(PROJECT)/LOC-FC.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -n $(NAME) -i FC $(COMPARE_LOC_OPTS) -o $@ -X --no-title

loc-plots/$(PROJECT)/LOC-FL.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -n $(NAME) -i FL $(COMPARE_LOC_OPTS) -o $@ -X --no-title

loc-plots/$(PROJECT)/LOC-ND.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -n $(NAME) -i CND $(COMPARE_LOC_OPTS) -o $@ -X --no-title

loc-plots/$(PROJECT)/LOC-NEG.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -n $(NAME) -i NEG $(COMPARE_LOC_OPTS) -o $@ --no-title
