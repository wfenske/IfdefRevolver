### Makefile to run statistics for a project

### From the environment
#PROJECT ?= $(PROJECT)
###

FISHER_CSV = results/$(PROJECT)/fisher.csv
ALL_R_DATA = results/$(PROJECT)/allData.rdata

INPUT_CSVS = $(wildcard results/$(PROJECT)/[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]/joint_function_ab_smell_snapshot.csv)

WINDOW_EXCLUSION_MARKER = results/$(PROJECT)/.last_window_exclusion

all: $(FISHER_CSV)

fisher: $(FISHER_CSV)

$(FISHER_CSV): $(ALL_R_DATA)
	if ! fisher.R -p $(PROJECT) > $(FISHER_CSV); \
	then \
		rm -f $(FISHER_CSV); \
		false; \
	fi

$(ALL_R_DATA): $(INPUT_CSVS) $(WINDOW_EXCLUSION_MARKER)
	rdata-from-csv.R -p $(PROJECT)
