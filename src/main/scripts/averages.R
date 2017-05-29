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
                metavar="FUNC",
                default="median",
                help="Name of the average function. Valid values are {mean,median}. [default: %default]")
  , make_option(c("--min"),
                default=NULL,
                metavar="FUNC",
                help="Optionally compute a minimum value. Valid values are {min,absmin}. [default: %default]")
  , make_option(c("--max"),
                default=NULL,
                metavar="FUNC",
                help="Optionally compute a minimum value. Valid values are {max,absmax}. [default: %default]")
  , make_option("--digits",
                default=2,
                help="Number of decimal digits to round to. [default: %default]")
  , make_option(c("-H", "--no-header"),
                dest="noHeader",
                default=FALSE,
                action="store_true",
                help="Omit the CSV header.")
)

args <- parse_args(OptionParser(description = "Compute the mean or median of the values in a named column of a CSV file. If no file is given, input is read from stdin.",
                                usage = "%prog [options] [file]",
                                option_list=options),
                   positional_arguments = c(0,1))
opts <- args$options

if (length(args$args) == 0) {
    inputFile <- file("stdin")
    open(inputFile)
} else if (length(args$args) == 1) {
    inputFile <- args$args[1]
} else {
    stop(paste("Invalid number of input files. Expected at most one, got",
               length(args$args)))
}

#cat(paste("inputFn=", inputFn, "; outputFn=", outputFn, sep=""))
#cat("\n")

#stop("")

#inputFn <- commandArgs(trailingOnly=TRUE)[1] # should be overview.csv or overviewSize.csv

data <- read.csv(file=inputFile, head=TRUE, sep=opts$delimiter)

values <- data[[opts$column]]

absmax <- function(x) { x[which.max( abs(x) )]}
absmin <- function(x) { x[which.min( abs(x) )]}
customRound <- function(x) { round(x, digits=opts$digits) }

## Parse `func' option
if (opts$func == "mean") {
    avgfunc <- function(c) {
        return (mean(c))
    }
} else if (opts$func == "median") {
    avgfunc <- function(c) {
        return (median(c))
    }
} else {
    eprintf("Invalid average function value: `%s'\n", opts$func)
    quit(status=1)
}

avgVal <- avgfunc(values)
rAvgVal <- customRound(avgVal)

## Parse `min' option
if (is.null(opts$min)) {
    rMinVal <- NULL
} else {
    fn <- opts$min
    if (fn == "min") {
        v <- min(values)
    } else if (fn == "absmin") {
        v <- absmin(values)
    } else {
        eprintf("Invalid min function value: `%s'\n", fn)
        quit(status=1)
    }
    rMinVal <- customRound(v)
}

## Parse `max' option
if (is.null(opts$max)) {
    rMaxVal <- NULL
} else {
    fn <- opts$max
    if (fn == "max") {
        v <- max(values)
    } else if (fn == "absmax") {
        v <- absmax(values)
    } else {
        eprintf("Invalid max function value: `%s'\n", fn)
        quit(status=1)
    }
    rMaxVal <- customRound(v)
}

### Print the header
if (!opts$noHeader) {
    if (!is.null(opts$min)) {
        printf("%s%s", opts$min, opts$delimiter)
    }
    printf("%s", opts$func)
    if (!is.null(opts$max)) {
        printf("%s%s", opts$delimiter, opts$max)
    }
    printf("\n")
}

### Print the actual values
if (!is.null(rMinVal)) {
    printf("%.*f%s", opts$digits, rMinVal, opts$delimiter)
}
printf("%.*f", opts$digits, rAvgVal)
if (!is.null(rMaxVal)) {
    printf("%s%.*f", opts$delimiter, opts$digits, rMaxVal)
}
printf("\n")
