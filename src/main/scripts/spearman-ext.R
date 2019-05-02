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
              , help="Name of the project whose data to load.  We expect the input R data to reside in `<projec-name>/results/allData.rdata' below the current working directory."
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
    description = "Compute the Spearman rank correlation coefficient between various annotation metrics and various change metrics. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
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

printHeader <- function() {
    dummy <- printf('System,ChangedOnly,AnnotatedOnly,Independent')
    for (dep in deps) {
        dummy <- printf(',%s', dep)
    }
    dummy <- printf('\n')
}

printRhoRow <- function(data, indep, systemName, changedOnly, annotatedOnly) {
    dummy <- printf("%s,%d,%d,%s", systemName, changedOnly, annotatedOnly, indep)
    x <- eval(parse(text=paste("data", indep, sep="$")))
    for (dep in deps) {
        y <- eval(parse(text=paste("data", dep, sep="$")))
        if (opts$warnings) {
            r <- cor.test(x, y, method="spearman")
        } else {
            r <- suppressWarnings(cor.test(x, y, method="spearman"))
        }
        rho <- r$estimate
        ##p <- r$p.value
        dummy <- printf(",%f", rho)
    }
    dummy <- printf('\n')
}

allData <- readData(args)

allData[is.na(allData)] <- 0.0

##indeps <- c("FL", "FC", "CND", "NEG", "LOAC", "LOC")
indeps <- c("CND", "FC", "FL", "LOAC", "LOC", "NEG") # alphabetical order
## NOTE, 2017-11-29, wf: Including log2LOC as an independent variable
## doesn't change anything regarding Spearman's rank correlation
## because Spearman only cares about order (not linearity), which
## stays the same whether log-transform or not.

deps <- c("COMMITS", "LCH"
        ##, "COMMITSratio", "LCHratio"
        ##, "COMMITSlog2Ratio", "LCHlog2Ratio"
        ##, "COMMITSsqrtRatio", "LCHsqrtRatio"
          )

##for (indep in indeps) {
##    eprintf("WARN: rounding independent variable values of `%s'!\n", indep)
##    allData[,indep] <- round(allData[,indep])
##}

if (! opts$no_header ) {
    dummy <- printHeader()
}

for (changedOnly in c(0, 1)) {
    filteredData <- allData
    if (changedOnly != 0) {
        filteredData <- subset(filteredData, COMMITS > 0)
    }
    for (annotatedOnly in c(0, 1)) {
        if (annotatedOnly != 0) {
            filteredData <- subset(filteredData, FL > 0)
        }
        for (indep in indeps) {
            dummy <- printRhoRow(filteredData, indep
                               , systemName, changedOnly, annotatedOnly)
        }
    }
}
