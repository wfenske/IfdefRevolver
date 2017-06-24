#!/usr/bin/env Rscript

library(optparse)

printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

options <- list(
    make_option(c("-d", "--delimiter"),
                default=",",
                help="Column delimiter of the input data, e.g. `,' or `;'. [default: %default]. (The output will always use `,' as the delimiter.)")
  , make_option(c("-c", "--column"),
                default=NULL,
                help="Name of the column to consider.")
  , make_option(c("-i", "--identifier"),
                default=NULL,
                help="Optional identifier of the summarized data. If present, it will be appended as the last column.")
  , make_option("--digits",
                default=2,
                help="Number of decimal digits to round to. [default: %default]")
  , make_option(c("-H", "--no-header"),
                dest="noHeader",
                default=FALSE,
                action="store_true",
                help="Omit the CSV header.")
)

args <- parse_args(OptionParser(description = "Report number of samples, mean, standard deviation, and standard error of the values in a named column of a CSV file. If no file is given, input is read from stdin.",
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
    eprintf("ERROR Invalid number of input files. Expected at most one, got `%d'\n", length(args$args))
    quit(status=1)
}

#cat(paste("inputFn=", inputFn, "; outputFn=", outputFn, sep=""))
#cat("\n")

#stop("")

#inputFn <- commandArgs(trailingOnly=TRUE)[1] # should be overview.csv or overviewSize.csv

data <- read.csv(file=inputFile, head=TRUE, sep=opts$delimiter)

if (is.null(opts$column)) {
    opts$column <- colnames(data)[[1]]
    eprintf("WARN: No column name specified. Using first column, %s.\n", opts$column)
}

customRound <- function(x) { round(x, digits=opts$digits) }
## Cf. https://stackoverflow.com/questions/2676554/in-r-how-to-find-the-standard-error-of-the-mean
stdErr <- function(x) sd(x)/sqrt(length(x))

values <- data[[opts$column]]

avgVal <- mean(values)
sdVal <- sd(values)
seVal <- stdErr(values)

rAvgVal <- customRound(avgVal)
rSdVal <- customRound(sdVal)
rSeVal <- customRound(seVal)

##eprintf("DEBUG %s\n", paste(values, collapse=", "))

### Print the header
if (!opts$noHeader) {
    identHead <- ""
    if (!is.null(opts$identifier)) {
        identHead <- ",Identifier"
    }
    printf("N,M(%s),SD(%s),SE(%s)%s\n" , opts$column, opts$column, opts$column,
           identHead)
}

### Print the actual values
identVal <- ""
if (!is.null(opts$identifier)) {
    identVal <- paste(',', opts$identifier, sep='')
}

printf("%d,%.*f,%.*f,%.*f%s\n",
       length(values)
      ,opts$digits, rAvgVal
     , opts$digits, rSdVal
     , opts$digits, rSeVal
     , identVal)
