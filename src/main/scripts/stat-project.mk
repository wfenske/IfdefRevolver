### Makefile to run statistics for a project

### From the environment
#PROJECT ?= $(PROJECT)
###

NAME ?= $(PROJECT)

FISHER_CSV = results/$(PROJECT)/fisher.csv
ALL_R_DATA = results/$(PROJECT)/allData.rdata

INPUT_CSVS = $(wildcard results/$(PROJECT)/[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/joint_function_ab_smell_snapshot.csv)

INDEPS = FL FC ND
PLOTS = \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -COMMITS.COUNT.pdf,$(INDEPS))) \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -COMMITS.LOC.pdf,$(INDEPS)))   \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -HUNKS.COUNT.pdf,$(INDEPS)))   \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -HUNKS.LOC.pdf,$(INDEPS)))     \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -LCH.COUNT.pdf,$(INDEPS)))     \
	$(addprefix ratios-plots/$(PROJECT)/ratios-,$(addsuffix -LCH.LOC.pdf,$(INDEPS)))

WINDOW_EXCLUSION_MARKER = results/$(PROJECT)/.last_window_exclusion

all: fisher ratiosplots

fisher: $(FISHER_CSV)

ratiosplots: $(PLOTS)

$(FISHER_CSV): $(ALL_R_DATA)
	if ! fisher.R -p $(PROJECT) > $(FISHER_CSV); \
	then \
		rm -f $(FISHER_CSV); \
		false; \
	fi

$(ALL_R_DATA): $(INPUT_CSVS) $(WINDOW_EXCLUSION_MARKER)
	rdata-from-csv.R -p $(PROJECT)

## INDEPS vs. COMMITS

ratios-plots/$(PROJECT)/ratios-FL-COMMITS.COUNT.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FL -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FC-COMMITS.COUNT.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FC -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-ND-COMMITS.COUNT.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i ND -d COMMITS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FL-COMMITS.LOC.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FL -d COMMITS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-FC-COMMITS.LOC.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FC -d COMMITS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-ND-COMMITS.LOC.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i ND -d COMMITS -s LOC -o $@

## INDEPS vs. HUNKS

ratios-plots/$(PROJECT)/ratios-FL-HUNKS.COUNT.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FL -d HUNKS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FC-HUNKS.COUNT.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FC -d HUNKS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-ND-HUNKS.COUNT.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i ND -d HUNKS -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FL-HUNKS.LOC.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FL -d HUNKS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-FC-HUNKS.LOC.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FC -d HUNKS -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-ND-HUNKS.LOC.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i ND -d HUNKS -s LOC -o $@

## INDEPS vs. LCH

ratios-plots/$(PROJECT)/ratios-FL-LCH.COUNT.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FL -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FC-LCH.COUNT.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FC -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-ND-LCH.COUNT.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i ND -d LCH -s COUNT -o $@

ratios-plots/$(PROJECT)/ratios-FL-LCH.LOC.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FL -d LCH -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-FC-LCH.LOC.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i FC -d LCH -s LOC -o $@

ratios-plots/$(PROJECT)/ratios-ND-LCH.LOC.pdf: $(ALL_R_DATA)
	mkdir -p `dirname $@` 
	ratioscmp.R -p $(PROJECT) -n $(NAME) -i ND -d LCH -s LOC -o $@
