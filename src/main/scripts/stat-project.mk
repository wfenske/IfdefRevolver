### Makefile to run statistics for a project

### From the environment
#PROJECT ?= $(PROJECT)
###

NAME ?= $(PROJECT)

RESULTS_DIR  = $(PROJECT)/results
LOG_DIR      = $(PROJECT)/logs

RDATA            = $(RESULTS_DIR)/joint_data.rds

SUBJECT_STATS_TEX = $(RESULTS_DIR)/subject_stats.tex
SUBJECT_STATS_LOG = $(LOG_DIR)/subject_stats.log

GROUP_DIFFS_CSV  = $(RESULTS_DIR)/group_differences.csv

GROUP_DIFFS_CHANGED_CSV  = $(RESULTS_DIR)/group_differences_changed.csv
GROUP_DIFFS_COMMITS_CSV  = $(RESULTS_DIR)/group_differences_commits.csv
GROUP_DIFFS_LCH_CSV      = $(RESULTS_DIR)/group_differences_lch.csv

GROUP_DIFFS_CHANGED_LOG  = $(LOG_DIR)/group_differences_changed.log
GROUP_DIFFS_COMMITS_LOG  = $(LOG_DIR)/group_differences_commits.log
GROUP_DIFFS_LCH_LOG      = $(LOG_DIR)/group_differences_lch.log

GROUP_DIFFS_CLIFFSD_FOLD_SIZE = 100000

SPEARMAN_CSV     = $(RESULTS_DIR)/spearman.csv
SPEARMAN_LOG     = $(LOG_DIR)/spearman.log

IFDEFREVOLVER_HOME ?= $(HOME)/src/skunk/IfdefRevolver/src/main/scripts

COMPARE_LOCS_PROG = $(IFDEFREVOLVER_HOME)/compare-locs.R
RATIOSCMP_PROG    = $(IFDEFREVOLVER_HOME)/ratioscmp.R
GROUP_DIFFS_PROG  = $(IFDEFREVOLVER_HOME)/test-group-differences.R
SPEARMAN_PROG     = $(IFDEFREVOLVER_HOME)/spearman.R
REG_COMMONS       = $(IFDEFREVOLVER_HOME)/regression-common.R
NBREG_PROG        = $(IFDEFREVOLVER_HOME)/nb.R
LOGITREG_PROG     = $(IFDEFREVOLVER_HOME)/logit.R
RDS_FROM_CSV_PROG = $(IFDEFREVOLVER_HOME)/rds-from-csv.R
STAT_SUBJECT_PROG = $(IFDEFREVOLVER_HOME)/stat-subject.sh

COMPARE_LOC_OPTS ?= --no-title --ymax=400
RDS_FROM_CSV_OPTS ?=

INPUT_CSV = $(RESULTS_DIR)/joint_data.csv

INDEPS = FL FC CND NEG
RATIOS_PLOTS = \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -COMMITS.COUNT.pdf,$(INDEPS))) \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -COMMITS.LOC.pdf,$(INDEPS)))   \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -LCH.COUNT.pdf,$(INDEPS)))     \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -LCH.LOC.pdf,$(INDEPS)))

LOC_PLOTS = $(addprefix $(RESULTS_DIR)/loc-plot-,$(addsuffix -x.pdf,$(INDEPS) LOACratio) $(addsuffix -nox.pdf,$(INDEPS) LOACratio))

NBREG_REGULAR_CSV              = $(RESULTS_DIR)/nb-reg.csv
NBREG_REGULAR_LOG              = $(LOG_DIR)/nb-reg.log

NBREG_BALANCED_CSV             = $(RESULTS_DIR)/nb-reg-balanced.csv
NBREG_BALANCED_LOG             = $(LOG_DIR)/nb-reg-balanced.log

NBREG_STD_CSV                  = $(RESULTS_DIR)/nb-reg-std.csv
NBREG_STD_LOG                  = $(LOG_DIR)/nb-reg-std.log

##NBREG_CHANGED_CSV              = $(RESULTS_DIR)/nb-reg-changed.csv
##NBREG_CHANGED_LOG              = $(LOG_DIR)/nb-reg-changed.log
##
##NBREG_ANNOTATED_CSV            = $(RESULTS_DIR)/nb-reg-annotated.csv
##NBREG_ANNOTATED_LOG            = $(LOG_DIR)/nb-reg-annotated.log
##
##NBREG_ANNOTATED_CHANGED_CSV    = $(RESULTS_DIR)/nb-reg-annotated-changed.csv
##NBREG_ANNOTATED_CHANGED_LOG    = $(LOG_DIR)/nb-reg-annotated-changed.log

LOGITREG_REGULAR_CSV           = $(RESULTS_DIR)/logit-reg.csv
LOGITREG_REGULAR_LOG           = $(LOG_DIR)/logit-reg.log

LOGITREG_BALANCED_CSV          = $(RESULTS_DIR)/logit-reg-balanced.csv
LOGITREG_BALANCED_LOG          = $(LOG_DIR)/logit-reg-balanced.log

LOGITREG_STD_CSV               = $(RESULTS_DIR)/logit-reg-std.csv
LOGITREG_STD_LOG               = $(LOG_DIR)/logit-reg-std.log

REGRESSIONMODELS = $(NBREG_REGULAR_CSV) $(LOGITREG_REGULAR_CSV) $(NBREG_BALANCED_CSV) $(LOGITREG_BALANCED_CSV) $(NBREG_STD_CSV) $(LOGITREG_STD_CSV)
##REGRESSIONMODELS = $(LOGITREG_REGULAR_CSV)
## $(NBREG_CHANGED_CSV) $(NBREG_ANNOTATED_CSV) $(NBREG_ANNOTATED_CHANGED_CSV) 

all: group_diffs locplots spearman regressionmodels subject_stats  #ratiosplots

group_diffs: $(GROUP_DIFFS_CSV)

spearman: $(SPEARMAN_CSV)

ratiosplots: $(RATIOS_PLOTS)

locplots: $(LOC_PLOTS)

regressionmodels: $(REGRESSIONMODELS)

subject_stats: $(SUBJECT_STATS_TEX)

$(SUBJECT_STATS_TEX): $(RDATA) $(STAT_SUBJECT_PROG) $(RESULTS_DIR)/commitParents.csv $(RESULTS_DIR)/revisionsFull.csv $(RESULTS_DIR)/snapshots.csv $(RESULTS_DIR)/domain.csv
	rm -f $@; \
	if ! $(STAT_SUBJECT_PROG) -p $(PROJECT) 2>&1 > $@|tee $(SUBJECT_STATS_LOG) >&2; \
	then \
		rm -f $@; \
		false; \
	fi

$(GROUP_DIFFS_CSV): $(GROUP_DIFFS_CHANGED_CSV) $(GROUP_DIFFS_COMMITS_CSV) $(GROUP_DIFFS_LCH_CSV)
	csvstack -d, -q '"' $^ > $@

$(GROUP_DIFFS_CHANGED_CSV): $(RDATA) $(GROUP_DIFFS_PROG)
	rm -f $@; \
	if ! $(GROUP_DIFFS_PROG) -p $(PROJECT) -d CHANGED 2>&1 > $@|tee $(GROUP_DIFFS_CHANGED_LOG) >&2; \
	then \
		rm -f $@; \
		false; \
	fi

$(GROUP_DIFFS_COMMITS_CSV): $(RDATA) $(GROUP_DIFFS_PROG)
	rm -f $@; \
	if ! $(GROUP_DIFFS_PROG) -p $(PROJECT) -d COMMITS -f $(GROUP_DIFFS_CLIFFSD_FOLD_SIZE) 2>&1 > $@|tee $(GROUP_DIFFS_COMMITS_LOG) >&2; \
	then \
		rm -f $@; \
		false; \
	fi

$(GROUP_DIFFS_LCH_CSV): $(RDATA) $(GROUP_DIFFS_PROG)
	rm -f $@; \
	if ! $(GROUP_DIFFS_PROG) -p $(PROJECT) -d LCH -f $(GROUP_DIFFS_CLIFFSD_FOLD_SIZE) 2>&1 > $@|tee $(GROUP_DIFFS_LCH_LOG) >&2; \
	then \
		rm -f $@; \
		false; \
	fi

$(SPEARMAN_CSV): $(RDATA) $(SPEARMAN_PROG)
	if !  $(SPEARMAN_PROG) -p $(PROJECT) 2>&1 > $(SPEARMAN_CSV)|tee $(SPEARMAN_LOG) >&2; \
	then \
		rm -f $(SPEARMAN_CSV); \
		false; \
	fi

$(NBREG_REGULAR_CSV): $(RDATA) $(NBREG_PROG) $(REG_COMMONS)
	if ! $(NBREG_PROG) -p $(PROJECT) 2>&1 > $(NBREG_REGULAR_CSV)|tee $(NBREG_REGULAR_LOG) >&2; \
	then \
		rm -f $(NBREG_REGULAR_CSV); \
		false; \
	fi

$(LOGITREG_REGULAR_CSV): $(RDATA) $(LOGITREG_PROG) $(REG_COMMONS)
	if ! $(LOGITREG_PROG) -p $(PROJECT) 2>&1 > $(LOGITREG_REGULAR_CSV)|tee $(LOGITREG_REGULAR_LOG) >&2; \
	then \
		rm -f $(LOGITREG_REGULAR_CSV); \
		false; \
	fi

$(NBREG_BALANCED_CSV): $(RDATA) $(NBREG_PROG) $(REG_COMMONS)
	if ! $(NBREG_PROG) -b -p $(PROJECT) 2>&1 > $(NBREG_BALANCED_CSV)|tee $(NBREG_BALANCED_LOG) >&2; \
	then \
		rm -f $(NBREG_BALANCED_CSV); \
		false; \
	fi

$(LOGITREG_BALANCED_CSV): $(RDATA) $(LOGITREG_PROG) $(REG_COMMONS)
	if ! $(LOGITREG_PROG) -b -p $(PROJECT) 2>&1 > $(LOGITREG_BALANCED_CSV)|tee $(LOGITREG_BALANCED_LOG) >&2; \
	then \
		rm -f $(LOGITREG_BALANCED_CSV); \
		false; \
	fi

$(NBREG_STD_CSV): $(RDATA) $(NBREG_PROG) $(REG_COMMONS)
	if ! $(NBREG_PROG) -s -p $(PROJECT) 2>&1 > $(NBREG_STD_CSV)|tee $(NBREG_STD_LOG) >&2; \
	then \
		rm -f $(NBREG_STD_CSV); \
		false; \
	fi

$(LOGITREG_STD_CSV): $(RDATA) $(LOGITREG_PROG) $(REG_COMMONS)
	if ! $(LOGITREG_PROG) -s -p $(PROJECT) 2>&1 > $(LOGITREG_STD_CSV)|tee $(LOGITREG_STD_LOG) >&2; \
	then \
		rm -f $(LOGITREG_STD_CSV); \
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

## LOC plots without x-axis labels and ticks
$(RESULTS_DIR)/loc-plot-FC-nox.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i FC        -X $(COMPARE_LOC_OPTS) -o $@

$(RESULTS_DIR)/loc-plot-FL-nox.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i FL        -X $(COMPARE_LOC_OPTS) -o $@

$(RESULTS_DIR)/loc-plot-CND-nox.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i CND       -X $(COMPARE_LOC_OPTS) -o $@

$(RESULTS_DIR)/loc-plot-NEG-nox.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i NEG       -X $(COMPARE_LOC_OPTS) -o $@

$(RESULTS_DIR)/loc-plot-LOACratio-nox.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i LOACratio -X $(COMPARE_LOC_OPTS) -o $@

## LOC plots with x-axis labels and ticks
$(RESULTS_DIR)/loc-plot-FC-x.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i FC           $(COMPARE_LOC_OPTS) -o $@

$(RESULTS_DIR)/loc-plot-FL-x.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i FL           $(COMPARE_LOC_OPTS) -o $@

$(RESULTS_DIR)/loc-plot-CND-x.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i CND          $(COMPARE_LOC_OPTS) -o $@

$(RESULTS_DIR)/loc-plot-NEG-x.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i NEG          $(COMPARE_LOC_OPTS) -o $@

$(RESULTS_DIR)/loc-plot-LOACratio-x.pdf: $(RDATA) $(COMPARE_LOCS_PROG)
	mkdir -p `dirname $@` 
	 $(COMPARE_LOCS_PROG) -p $(PROJECT) -i LOACratio    $(COMPARE_LOC_OPTS) -o $@
