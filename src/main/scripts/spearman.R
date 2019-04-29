#!/usr/bin/env Rscript

### Performs a Spearman rank correlation test of LOC depending on various annotation markers.
###
### Input file is the data set for the subject system.
##
## The interpretation of Spearman's rho is similar to that of
## Pearsons, e.g. the closer is to +/-1, the stronger the monotonic
## relationship. We can verbally describe the strength of the
## correlation using the following guide for the absolute value of rho:
##
## * .00-.19 ``very weak''
## * .20-.39 ``weak''
## * .40-.59 ``moderate''
## * .60-.79 ``strong''
## * .80-1.0 ``very strong''

library(optparse)

cmdArgs <- commandArgs(trailingOnly = FALSE)
file.arg.name <- "--file="
script.fullname <- sub(file.arg.name, "",
                       cmdArgs[grep(file.arg.name, cmdArgs)])
script.dir <- dirname(script.fullname)
source(file.path(script.dir, "regression-common.R"))

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `results/<projec-name>/joint_data.rds' below the current working directory."
              , default = NULL
                )
  , make_option(c("-H", "--no-header")
              , dest="no_header"
              , default=FALSE
              , action="store_true"
              , help="Omit header row. [default: %default]")
  , make_option(c("-s", "--system-name")
              , dest="systemName"
              , default=NULL
              , help="Name of the subject system, in case the `-p' option is not used. Defaults to the value of `-p'.")
  , make_option(c("--debug")
              , help="Print some diagnostic messages. Implies `-W'. [default: %default]"
              , default = FALSE
              , action="store_true"
                )
  , make_option(c("-W", "--warnings")
              , help='The R function that computes the correlation coefficient constantly complains that it "cannot compute exact p-value with ties.". This option controls whether to show that warning. [default: %default]'
              , default = FALSE
              , action="store_true"
                )
)

args <- parse_args(OptionParser(
    description = "Compute the Spearman rank correlation coefficient between various annotation metrics and LOC, as well as a selection of other correlations. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options
if (opts$debug) {
    opts$warnings <- TRUE
}

systemName <- NULL
if (!is.null(opts$projectName)) {
    systemName <<- opts$systemName
} else if (!is.null(opts$project)) {
    systemName <<- opts$project
} else {
    stop("Need to specify a system name, either explicitly via `-s'/`--system-name' or implicitly `-p'/`--project'.")
}

## * .00-.19 ``very weak''
## * .20-.39 ``weak''
## * .40-.59 ``moderate''
## * .60-.79 ``strong''
## * .80-1.0 ``very strong''
effectSizeClass <- function(rho) {
    v <- abs(rho)
    if (v < 0.2) { return ("very weak"); }
    else if (v < 0.4) { return ("weak"); }
    else if (v < 0.6) { return ("moderate"); }
    else if (v < 0.8) { return ("strong"); }
    else { return ("very strong"); }
}

printHeader <- function() {
    dummy <- printf('System,Balanced,D,I,rho,p,Magnitude\n')
}

printRhoRow <- function(data, indep, dep, systemName, balanced) {
    x <- data[,indep]
    y <- data[,dep]
    if (opts$warnings) {
        r <- cor.test(x, y, method="spearman")
    } else {
        r <- suppressWarnings(cor.test(x, y, method="spearman"))
    }
    rho <- r$estimate
    p <- r$p.value
    magnitude <- effectSizeClass(rho)
    cbalanced <- ifelse(balanced, "T", "F")
    dummy <- printf("%s,%s,%9s,%9s,% 7.4f,%12g,%s\n",
                    systemName, cbalanced, dep, indep, rho, p, magnitude)
}

allData <- readData(args)
corrData <- removeNaFunctions(allData)

if (! opts$no_header ) {
    dummy <- printHeader()
}

for (balanced in c(FALSE, TRUE)) {
    if (balanced) {
        data <- balanceAnnotatedAndUnannotatedFunctions(corrData)
    } else {
        data <- corrData
    }
    for (dep in c('LOC', 'COMMITS', 'LCH')) {
        dummy <- printRhoRow(data, 'FC',        dep, systemName, balanced)
        dummy <- printRhoRow(data, 'FL',        dep, systemName, balanced)
        dummy <- printRhoRow(data, 'CND',       dep, systemName, balanced)
        dummy <- printRhoRow(data, 'NEG',       dep, systemName, balanced)
        dummy <- printRhoRow(data, 'LOAC',      dep, systemName, balanced)
        dummy <- printRhoRow(data, 'LOACratio', dep, systemName, balanced)
        ## Control variables
        if (dep != 'LOC') {
            dummy <- printRhoRow(data, 'LOC',   dep, systemName, balanced)
        }
        dummy <- printRhoRow(data, 'AGE',       dep, systemName, balanced)
        dummy <- printRhoRow(data, 'MRC',       dep, systemName, balanced)
        dummy <- printRhoRow(data, 'PC',        dep, systemName, balanced)
    }
}
