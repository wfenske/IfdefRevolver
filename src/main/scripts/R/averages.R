#!/usr/bin/env Rscript

library(optparse)

printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

options <- list(
      make_option(c("-d", "--delimiter"),
                  default=",",
                  help="Column delimiter, e.g. `,' or `;'. [default: %default]")
    , make_option(c("-c", "--column"),
                default="ANY_SLOCMedianS",
                  help="Name of the column over which to average. [default: %default]")
    , make_option(c("-f", "--func"),
                  default="median",
                  help="Name of the average function. Valid values are {mean,median}. [default: %default]")
    , make_option("--digits",
                  default=0,
                  help="Number of decimal digits to round to. [default: %default]")
    , make_option(c("-v", "--verbose"),
                  default=FALSE,
                  action="store_true",
                  help="Be more verbose when printing the result.")
)

args <- parse_args(OptionParser(description = "Compute the mean or median of the values in a named column of a CSV file.",
                                usage = "%prog [options] file",
                                option_list=options),
                   positional_arguments = 1)
opts <- args$options
inputFn <- args$args

#cat(paste("inputFn=", inputFn, "; outputFn=", outputFn, sep=""))
#cat("\n")

#stop("")

#inputFn <- commandArgs(trailingOnly=TRUE)[1] # should be overview.csv or overviewSize.csv

data <- read.csv(file=inputFn, head=TRUE, sep=opts$delimiter)

## <smellkind>_FS	# files w/ smells
## <smellkind>_FNS	# files w/o smells
## <smellkind>_BS	# number of bug fixes to smelly files
## <smellkind>_BNS	# number of bug fixes to non-smelly files

## <smellkind>_FSB	# files w/ the smell w/ at least one bug fix
## <smellkind>_FSNB	# files w/ the smell but w/o any bug fixes
## <smellkind>_FNSB	# files w/o the smell w/ at least one bug fix
## <smellkind>_FNSNB	# files w/o the smell and w/o any bug fixes

## <smellkind>_SLOCMeanS
## <smellkind>_SLOCMeanNS
## <smellkind>_SLOCMedianS
## <smellkind>_SLOCMedianNS

values <- data[[opts$column]]

if (opts$func == "mean") {
    avgfunc <- function(c) {
        return (mean(c))
    }
} else {
    if (opts$func == "median") {
        avgfunc <- function(c) {
            return (median(c))
        }
    } else {
        eprintf("Invalid average function value: `%s'\n", opts$func)
        quit(status=1)
    }
}

a <- avgfunc(values)
ra <- round(a, digits=opts$digits)
if ( opts$verbose ) {
    printf("The rounded %s of values in %s column is %.*f\n",
           opts$func, opts$column, opts$digits, ra)
} else {
    printf("%.*f\n", opts$digits, ra)
}
