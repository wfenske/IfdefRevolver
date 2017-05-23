#!/usr/bin/env Rscript

### Performs linear regression the totals over all snapshots of a system
###
### Input files are the snapshot files under Correlated/

## Example by Fisher:

library(optparse)

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect a directory named `results/<projec-name>' to exist below the current working directory, and this directory is expected to contain directories named, e.g., `1996-07-01', which, in turn, contain CSV files named `joint_function_ab_smell_snapshot.csv'."
              , default = NULL
                )
)

args <- parse_args(OptionParser(
    description = "Read a project's data (as CSV files) and create R data from it. If no input files are named, the project name must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file ...]"
  , option_list=options)
  , positional_arguments = c(0, Inf))
opts <- args$options

outFile <- "allData.rdata"

getInputFilenames <- function(commandLineArgs) {
    result <- commandLineArgs$args
    if ( length(result) > 0 ) {
        return (result)
    }

    opts <- commandLineArgs$options
    if ( is.null(opts$project) ) {
            stop("Missing input files.  Either specify explicit input files or specify the name of the project the `--project' option (`-p' for short).")
    }

    baseDir <-  file.path("results", opts$project)
    snapshotResultDirs <- list.files(baseDir, pattern="[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]")
    if (length(snapshotResultDirs) == 0) {
        stop(paste("No snapshot directories found in `", baseDir, "'", sep=""))
    }

    outFile <<- file.path(baseDir, "allData.rdata")
    
    return (lapply(snapshotResultDirs, function(snapshotDir) { file.path(baseDir, snapshotDir, "joint_function_ab_smell_snapshot.csv") }))
}

snapshotIx <- 1

readSnapshotFile <- function(inputFn) {
    snapshotData <- read.csv(inputFn, header=TRUE, sep=",",
                             colClasses=c(
                                 "SNAPSHOT_DATE"="character"
                               , "FUNCTION_SIGNATURE"="character"
                               , "FUNCTION_LOC"="numeric"
                               , "HUNKS"="numeric"
                               , "COMMITS"="numeric"
                               ##, "BUGFIXES"="numeric"
                               ##, "LINE_DELTA"="numeric"
                               ##, "LINES_DELETED"="numeric"
                               ##, "LINES_ADDED"="numeric"
                               , "LOAC"="numeric"
                               , "LINES_CHANGED"="numeric"
                               , "LOFC"="numeric"
                               , "NOFL"="numeric"
                               , "NOFC_Dup"="numeric"
                               , "NOFC_NonDup"="numeric"
                               , "NONEST"="numeric"))
    cat("INFO: Reading snapshot ", snapshotData$SNAPSHOT_DATE[1], "\n", sep="")
    snapshotData["SNAPSHOT"] <- snapshotIx
    snapshotData[is.na(snapshotData)] = 0
    ## Change the value of the global variable using <<-
    ##cat(str(max(as.numeric(snapshotData$FUNCTION_LOC), na.rm=T)))
    ##cat(str(max(snapshotData$FUNCTION_LOC), na.rm=T))
    snapshotIx <<- snapshotIx + 1
    return (snapshotData)
}

inputFns <- getInputFilenames(args)

allData <- do.call("rbind", lapply(inputFns, readSnapshotFile))

### Independent variables for taking smell presence into account

## Independent variables for taking file size into account
##topSLOCValue <- quantile(data$SLOC, 1.0 - opts$large / 100.0)
##data$binLARGE <- data$SLOC > topSLOCValue

### Independent variables
## FUNCTION_LOC,LOAC,LOFC,NOFL,NOFC_Dup,NOFC_NonDup,NONEST

## Some renaming
allData$LCH <- allData$LINES_CHANGED

allData$LOC <- allData$FUNCTION_LOC
allData$logLOC <- log(allData$LOC)

## LOAC and LOFC are taken as is, but also log() and ratio (to LOC)
allData$logLOAC <- log(allData$LOAC + 1)
allData$LOACratio <- allData$LOAC / allData$LOC

allData$logLOFC <- log(allData$LOFC + 1)
allData$LOFCratio <- allData$LOFC / allData$LOC

allData$FL <- allData$NOFL
allData$FLratio <- allData$FL / allData$LOC

allData$FC <- allData$NOFC_NonDup
allData$FCratio <- allData$FC / allData$LOC

allData$ND <- allData$NONEST
allData$NDratio <- allData$ND / allData$LOC

### Dependent variables
## HUNKS,COMMITS,LINES_CHANGED,LINE_DELTA,LINES_DELETED,LINES_ADDED
allData$HUNKSratio <- allData$HUNKS / allData$LOC
allData$COMMITSratio <- allData$COMMITS / allData$LOC

allData$LCHratio <- allData$LINES_CHANGED / allData$LOC

### Compute some more independent variables from the data
##data$CHANGE_PRONE <- data$COMMITS >= opts$changes

saveRDS(allData, file=outFile)
cat("INFO: Successfully wrote ", outFile, "\n", sep="")
