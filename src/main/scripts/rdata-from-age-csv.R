#!/usr/bin/env Rscript

### Performs linear regression the totals over all snapshots of a system
###
### Input files are the snapshot files under Correlated/

library(optparse)

printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect a directory named `results/<projec-name>' to exist below the current working directory, and this directory is expected to contain directories named, e.g., `1996-07-01', which, in turn, contain CSV files named `joint_function_ab_smell_age_snapshot.csv'."
              , default = NULL
                )
)

args <- parse_args(OptionParser(
    description = "Read a project's data (as CSV files) and create R data from it. If no input files are named, the project name must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file ...]"
  , option_list=options)
  , positional_arguments = c(0, Inf))
opts <- args$options

outFile <- "allDataAge.rdata"

getInputFilename <- function(commandLineArgs) {
    result <- commandLineArgs$args
    if ( length(result) > 0 ) {
        return (result[1])
    }

    opts <- commandLineArgs$options
    if ( is.null(opts$project) ) {
            stop("Missing input files.  Either specify an explicit input file or specify the name of the project the `--project' option (`-p' for short).")
    }

    baseDir <- file.path("results", opts$project)
    inputFn <-  file.path(baseDir, "joint_function_ab_smell_age_snapshot.csv")
    outFile <<- file.path(baseDir, "allDataAge.rdata")
    
    return (inputFn)
}

iround <- function(x) {
    return (as.integer(round(x)))
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

allData <- readSnapshotFile(inputFn)

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
allData$LCH <- allData$LINES_CHANGED

allData$logLOC <- log(allData$LOC)


## LOAC and LOFC are taken as is, but also log() and ratio (to LOC)
allData$logLOAC <- log(allData$LOAC + 1)
allData$LOACratio <- allData$LOAC / allData$LOC

allData$logLOFC <- log(allData$LOFC + 1)
allData$LOFCratio <- allData$LOFC / allData$LOC

allData$FLratio <- allData$FL / allData$LOC

allData$FC <- allData$FC_NonDup
allData$FCratio <- allData$FC / allData$LOC

allData$CNDratio <- allData$CND / allData$LOC

allData$NEGratio <- allData$NEG / allData$LOC

### Dependent variables
## HUNKS,COMMITS,LINES_CHANGED,LINE_DELTA,LINES_DELETED,LINES_ADDED
allData$COMMITSratio <- allData$COMMITS / allData$LOC
allData$LCHratio <- allData$LINES_CHANGED / allData$LOC

### Compute some more independent variables from the data
##data$CHANGE_PRONE <- data$COMMITS >= opts$changes

saveRDS(allData, file=outFile)
cat("INFO: Successfully wrote ", outFile, "\n", sep="")
