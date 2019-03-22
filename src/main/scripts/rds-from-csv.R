#!/usr/bin/env Rscript

### Performs linear regression the totals over all snapshots of a system
###
### Input files are the snapshot files under Correlated/

library(optparse)

printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect a directory named `results/<projec-name>' to exist below the current working directory, and this directory is expected to contain a CSV file named `all_data.csv'."
              , default = NULL
                )
  , make_option(c("-T", "--no-test-code"),
                default=FALSE,
                action="store_true",
                dest="noTestCode",
                help="Exclude functions that likely constitute test code. A simple heuristic based on file name and function name is used to identify such functions. [default: %default]")
  , make_option(c("-E", "--exclude-files")
              , help="Exclude functions residing in files whose pathnames match the given regular expression."
              , default = NULL
              , metavar = "RE"
              , dest="excludeFilesRe"
                )
  , make_option(c("-o", "--output")
              , help="Name of the output file.  If omitted, the project name must be specified using the `-p' option, and the output file will be saved under `results/<projec-name>/allDataAge.rdata'."
              , default = NULL
                )
)

optionsParser <- OptionParser(
    description = "Read a project's data (as a CSV file) and create R data from it. If no input files are named, the project name must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)

args <- parse_args(optionsParser, positional_arguments = c(0, 1))
opts <- args$options

getInputFilename <- function(commandLineArgs) {
    result <- commandLineArgs$args
    if ( length(result) > 0 ) {
        return (result[1])
    }

    opts <- commandLineArgs$options
    if ( is.null(opts$project) ) {
            stop("Missing input files.  Either specify an explicit input file or specify the name of the project the `--project' option (`-p' for short).")
    }

    baseDir <- file.path(opts$project, "results")
    inputFn <-  file.path(baseDir, "joint_data.csv")
    
    return (inputFn)
}

getOutputFilename <- function(commandLineArgs) {
    opts <- commandLineArgs$options
    if ( ! is.null(opts$output) ) {
        return (opts$output)
    }
    
    if ( is.null(opts$project) ) {
            stop("Missing input files.  Either specify an explicit input file or specify the name of the project the `--project' option (`-p' for short).")
    }

    baseDir <- file.path(opts$project, "results")
    outputFn <- file.path(baseDir, "joint_data.rds")

    return (outputFn)
}

iround <- function(x) {
    return (as.integer(round(x)))
}

removeFunctionsInFilesMatching <- function(df, excludeFilesRe) {
    eprintf("DEBUG: Removing functions in files matching `%s'\n", excludeFilesRe)
    filteredData <- subset(df, !(grepl(excludeFilesRe, FILE)))
    nrowBefore <- nrow(df)
    nrowAfter <- nrow(filteredData)
    nrowRemoved <- nrowBefore - nrowAfter
    percentageRemoved <- (100.0 * nrowRemoved) / nrowBefore
    eprintf("DEBUG: #rows before before: %d\n", nrowBefore)
    eprintf("DEBUG: #rows after: %d\n", nrowAfter)
    eprintf("DEBUG: #rows removed: %d\n", nrowRemoved)
    eprintf("DEBUG: Removed %d out of %d rows (%.1f%%)\n", nrowRemoved, nrowBefore, percentageRemoved)
    return (filteredData)
}

removeTestFunctions <- function(df) {
    eprintf("DEBUG: Removing test functions\n")
    filteredData <- subset(allData, ! (grepl(" test", FUNCTION_SIGNATURE) | grepl("Test", FUNCTION_SIGNATURE) | grepl("^test", FILE)))
    nrowBefore <- nrow(df)
    nrowAfter <- nrow(filteredData)
    nrowRemoved <- nrowBefore - nrowAfter
    percentageRemoved <- (100.0 * nrowRemoved) / nrowBefore
    eprintf("DEBUG: #rows before: %d\n", nrowBefore)
    eprintf("DEBUG: #rows after: %d\n", nrowAfter)
    eprintf("DEBUG: #rows removed: %d\n", nrowRemoved)
    eprintf("DEBUG: Removed %d out of %d rows (%.1f%%)\n", nrowRemoved, nrowBefore, percentageRemoved)
    return (filteredData)
}

readSnapshotFile <- function(inputFn) {
    eprintf("INFO: Reading data from %s\n", inputFn)
    snapshotData <- read.csv(inputFn, header=TRUE, sep=",",
                             colClasses=c(
                                 "SNAPSHOT_DATE"="character"
                               , "SNAPSHOT_INDEX"="numeric"
                               , "FUNCTION_UID"="numeric"
                               , "FUNCTION_SIGNATURE"="character"
                               , "FILE"="character"
                               , "AGE"="numeric"
                               , "LAST_EDIT"="numeric"
                               , "COMMITS"="numeric"
                               , "LINES_CHANGED"="numeric"
                               , "LINES_ADDED"="numeric"
                               , "LINES_DELETED"="numeric"
                               , "PREVIOUS_COMMITS"="numeric"
                               , "PREVIOUS_LINES_CHANGED"="numeric"
                               , "LOC"="numeric"
                               , "LOAC"="numeric"
                               , "LOFC"="numeric"
                               , "FL"="numeric"
                               , "FC_Dup"="numeric"
                               , "FC_NonDup"="numeric"
                               , "CND"="numeric"
                               , "NEG"="numeric"))
    snapshotDate <- snapshotData$SNAPSHOT_DATE[1]
    ##eprintf("INFO: Read snapshot %s\n", snapshotDate)
    ##snapshotData[is.na(snapshotData)] = 0

    changedFuncs <- subset(snapshotData, COMMITS > 0)

    ## Compute snashot-specific averages for dependent variables

    ## COMMITS
    medianChangedFuncsCommits <- median(changedFuncs$COMMITS)
    eprintf("DEBUG: median commits of changed functions: %.3f\n", medianChangedFuncsCommits)
    snapshotData["MEDIAN_SNAPSHOT_CH_COMMITS"] <- medianChangedFuncsCommits

    changedFuncs$COMMITSratio <- changedFuncs$COMMITS / changedFuncs$LOC
    medianChangedFuncsCommitsRatio <- median(changedFuncs$COMMITSratio)
    eprintf("DEBUG: median commit ratio of changed functions: %.3f\n", medianChangedFuncsCommitsRatio)
    snapshotData["MEDIAN_SNAPSHOT_CH_COMMITSratio"] <- medianChangedFuncsCommitsRatio

    ## LCH
    medianChangedFuncsLch <- median(changedFuncs$LINES_CHANGED)
    eprintf("DEBUG: median lines changed of changed functions: %.3f\n", medianChangedFuncsLch)
    snapshotData["MEDIAN_SNAPSHOT_CH_LCH"] <- medianChangedFuncsLch

    changedFuncs$LCHratio <- changedFuncs$LINES_CHANGED / changedFuncs$LOC
    medianChangedFuncsLchRatio <- median(changedFuncs$LCHratio)
    eprintf("DEBUG: median lines changed ratio of changed functions: %.3f\n", medianChangedFuncsLchRatio)
    snapshotData["MEDIAN_SNAPSHOT_CH_LCHratio"] <- medianChangedFuncsLchRatio
    
    return (snapshotData)
}

inputFn <- getInputFilename(args)
outputFile <- getOutputFilename(args)

allData <- readSnapshotFile(inputFn)

if (opts$noTestCode) {
    allData <- removeTestFunctions(allData)
}

if (!is.null(opts$excludeFilesRe)) {
    allData <- removeFunctionsInFilesMatching(allData, opts$excludeFilesRe)
}

allData$LOC <- iround(allData$LOC)
allData$LOAC <- iround(allData$LOAC)
allData$LOFC <- iround(allData$LOFC)
allData$FL <- iround(allData$FL)
allData$FC_Dup <- iround(allData$FC_Dup)
allData$FC_NonDup <- iround(allData$FC_NonDup)
allData$CND <- iround(allData$CND)
allData$NEG <- iround(allData$NEG)

numMissingAge <- nrow(subset(allData, is.na(AGE)))
dataWithAge <- subset(allData, !is.na(AGE))
numHaveAge <- nrow(dataWithAge)
percentMissingAge <- iround(100*numMissingAge/nrow(allData))
if (numMissingAge > 0) {
    eprintf("WARN: missing age: %d, have age: %d (%d%% missing)\n", numMissingAge, numHaveAge, percentMissingAge)
    ##naSubstVal <- 0 # median(dataWithAge$AGE)
    ##allData$AGE[is.na(allData$AGE)] <- naSubstVal
}

numMissingEdit <- nrow(subset(allData, is.na(LAST_EDIT)))
dataWithEdit <- subset(allData, !is.na(LAST_EDIT))
numHaveEdit <- nrow(dataWithEdit)
percentMissingEdit <- iround(100*numMissingEdit/nrow(allData))
if (numMissingEdit > 0) {
    eprintf("WARN: missing last edit: %d, have last edit: %d (%d%% missing)\n", numMissingEdit, numHaveEdit, percentMissingEdit)
    ##naSubstVal <- 0 #median(dataWithEdit$LAST_EDIT)
    ##allData$LAST_EDIT[is.na(allData$LAST_EDIT)] <- naSubstVal
}

dataWithAge  <- subset(allData, !is.na(AGE))
dataWithEdit <- subset(allData, !is.na(LAST_EDIT))

changedDataWithAge  <- subset(dataWithAge,  COMMITS > 0)
changedDataWithEdit <- subset(dataWithEdit, COMMITS > 0)

unchangedDataWithAge  <- subset(dataWithAge,  COMMITS == 0)
unchangedDataWithEdit <- subset(dataWithEdit, COMMITS == 0)

eprintf("Median AGE/LAST_EDIT of all functions:\n%.0f,%.0f\n"
      , median(dataWithAge$AGE)
      , median(dataWithEdit$LAST_EDIT))

eprintf("Median AGE/LAST_EDIT of changed functions:\n%.0f,%.0f\n"
      , median(changedDataWithAge$AGE)
      , median(changedDataWithEdit$LAST_EDIT))

eprintf("Median AGE/LAST_EDIT of unchanged functions:\n%.0f,%.0f\n"
      , median(unchangedDataWithAge$AGE)
      , median(unchangedDataWithEdit$LAST_EDIT))

eprintf("Mean AGE/LAST_EDIT of all functions:\n%.0f,%.0f\n"
      , mean(dataWithAge$AGE)
      , mean(dataWithEdit$LAST_EDIT))

eprintf("Mean AGE/LAST_EDIT of changed functions:\n%.0f,%.0f\n"
      , mean(changedDataWithAge$AGE)
      , mean(changedDataWithEdit$LAST_EDIT))

eprintf("Mean AGE/LAST_EDIT of unchanged functions:\n%.0f,%.0f\n"
      , mean(unchangedDataWithAge$AGE)
      , mean(unchangedDataWithEdit$LAST_EDIT))


### Independent variables for taking smell presence into account

### Independent variables
## LOC,LOAC,LOFC,NOFL,NOFC_Dup,NOFC_NonDup,NONEST

## Some renaming
allData$LCH  <- allData$LINES_CHANGED
##allData$PLCH <- allData$PREVIOUS_LINES_CHANGED
## PC = (Number of) previous changes
allData$PC   <- allData$PREVIOUS_COMMITS
## MRC = (time since) most recent change
allData$MRC  <- allData$LAST_EDIT

## LOAC and LOFC are taken as is, but also log() and ratio (to LOC)
##allData$logLOAC <- log(allData$LOAC + 1)
allData$LOACratio <- allData$LOAC / allData$LOC

##allData$logLOFC <- log(allData$LOFC + 1)
allData$LOFCratio <- allData$LOFC / allData$LOC

##allData$FLratio <- allData$FL / allData$LOC

allData$FC        <- allData$FC_NonDup

##allData$FCratio  <- allData$FC / allData$LOC
##allData$CNDratio <- allData$CND / allData$LOC
##allData$NEGratio <- allData$NEG / allData$LOC


## Calculate some log-scaled variables
allData$log2FL   <- log2(allData$FL + 1)
allData$log2FC   <- log2(allData$FC + 1)
allData$log2CND  <- log2(allData$CND + 1)
allData$log2NEG  <- log2(allData$NEG + 1)
allData$log2LOAC <- log2(allData$LOAC + 1)

allData$log2LOC  <- log2(allData$LOC)

allData$log2AGE  <- log2(allData$AGE + 1)
allData$log2MRC  <- log2(allData$MRC + 1)
allData$log2PC   <- log2(allData$PC + 1)

### Dependent variables
## HUNKS,COMMITS,LINES_CHANGED,LINE_DELTA,LINES_DELETED,LINES_ADDED
allData$COMMITSratio <- allData$COMMITS / allData$LOC
allData$LCHratio     <- allData$LCH     / allData$LOC

### Compute some more independent variables from the data
##data$CHANGE_PRONE <- data$COMMITS >= opts$changes

saveRDS(allData, file=outputFile)
cat("INFO: Successfully wrote ", outputFile, "\n", sep="")
