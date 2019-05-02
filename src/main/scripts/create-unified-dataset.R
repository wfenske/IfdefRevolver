#!/usr/bin/env Rscript

library(optparse)
library(parallel)

## cmdArgs <- commandArgs(trailingOnly = FALSE)
## file.arg.name <- "--file="
## script.fullname <- sub(file.arg.name, "",
##                        cmdArgs[grep(file.arg.name, cmdArgs)])
## script.dir <- dirname(script.fullname)
## source(file.path(script.dir, "regression-common.R"))

options <- list(
    make_option(c("-o", "--output")
              , help="Name of the output file"
              , default=NULL
                )
    , make_option(c("-s", "--sample-size")
                , help="Total number of data points taken from each input file [default: %default]"
                , dest="sample_size"
                , default=50000
                )
  , make_option(c("-b", "--balance"),
                default=FALSE,
                action="store_true",
                help="Balance input data so that there is an equal amount of annotated and non-annotated functions [default: %default]")
)

args <- parse_args(OptionParser(
    description = "Create a unified dataset in which every input dataset is represented to an equal degree."
  , usage = "%prog [options]  FILE [FILE ...]"
  , option_list=options)
  , positional_arguments = c(1, Inf))
opts <- args$options

options(mc.cores = detectCores())

printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

downOrUpSample <- function(df, sz, logSuffix="") {
    rowCount <- nrow(df)
    if (rowCount < sz) {
        ## Upsample
        eprintf("DEBUG: Upsampling from %d to %d%s\n", rowCount, sz, logSuffix)
        return (df[sample(rowCount, sz, replace=TRUE), ])
    } else {
        ## Downsample
        eprintf("DEBUG: Downsampling from %d to %d%s\n", rowCount, sz, logSuffix)
        return (df[sample(rowCount, sz), ])
    }
}

nTotal <- as.integer(opts$sample_size)
if (opts$balance) {
    nU <- as.integer(round(nTotal/2))
    nA <- nTotal - nU
    sampleDataSet <- function(df, fname="") {
        dfA   <- subset(df, FL > 0)
        dfU   <- subset(df, FL == 0)
        rbind(downOrUpSample(dfA, nA, logSuffix=sprintf(" annotated functions in `%s'", fname)),
              downOrUpSample(dfU, nU, logSuffix=sprintf(" unannotated functions in `%s'", fname)))
    }
} else {
    sampleDataSet <- function(df, fname="") {
        downOrUpSample(df, nTotal, logSuffix=sprintf(" functions in `%s'", fname))
    }
}

readAndSampleDataSet <- function(rdsFn) {
    eprintf("DEBUG: Reading data from %s\n", rdsFn)
    df <- readRDS(rdsFn)
    df$DATASET_SOURCE <- rdsFn
    eprintf("DEBUG: Sucessfully read dataset %s.\n", rdsFn)
    sampleDataSet(df, fname=rdsFn)
}

if (is.null(opts$output)) {
    stop("Missing output filename.  Specify via option `-o'.")
}

allData <- Reduce(rbind, mclapply(args$args, readAndSampleDataSet))

saveRDS(allData, file=opts$output)

eprintf("INFO: Successfully computed unified dataset.\n")
