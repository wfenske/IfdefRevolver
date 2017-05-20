#!/usr/bin/env Rscript

### Performs Fisher's test on the totals over all windows of a system
###
### Input files are eiither overview.csv or overviewSize.csv

## Example by Fisher:

##Convictions <- matrix(c(2, 10, 15, 3)
##                      , nrow = 2
##                      , dimnames = list(Twins = c('Dizygotic', 'Monozygotic')
##                                      , Status = c('Convicted', 'Not convicted')))
##
##              Status
## Twins        Convicted Not convicted
## Dizygotic     2             15
## Monozygotic  10              3
##
##              Status
## Twins        Convicted Not convicted
## Dizygotic     dc             dn
## Monozygotic   mc             mn
##
## --> first arg to matrix command has the following form:
##
##     c(dc, mc, dn, mn)

## Table make-up
##
##   xxx    | fixed | not fixed
##   smelly | s-f   |   s-nf
##   clean  | c-f   |   c-nf
##
## --> smelly-fixed, clean-fixed, smelly-not-fixed, clean-not-fixed

library(optparse)

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `results/<projec-name>/allData.rdata' below the current working directory."
              , default = NULL
                )
  , make_option(c("-i", "--independent")
              , help="Name of the independent variable (e.g., FL, LOC)."
              , default = NULL
                )
  , make_option(c("--ithresh")
              , help="Absolute threshold value for the independent variable. If a function's metric value for the independent variable is <= than this threshold, the function is considered to not have the marker. If the value is above the threshold, the function has the marker. If not specified, the median value of the independent variable is chosen as the threshold."
              , metavar = "NUMBER"
              , type = "numeric"
              , default = NULL
                )
  , make_option(c("-d", "--dependent")
              , help="Name of the dependent variable (e.g., COMMITS, COMMITSratio, LCHratio)."
              , default = NULL
                )
  , make_option(c("-H", "--no_header"),
                default=FALSE,
                action="store_true",
                help="Omit header row. [default: %default]")
  , make_option(c("--debug")
              , help="Print some diagnostic messages. [default: %default]"
              , default = FALSE
              , action="store_true"
                )
)

args <- parse_args(OptionParser(
    description = "Perform Fisher's exact test to determine which independent variables have a significant effect on functions being (or not) change-prone. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

readData <- function(commandLineArgs) {
    fns <- commandLineArgs$args
    if ( length(fns) == 1 ) {
        dataFn <- fns[1]
    } else {
        opts <- commandLineArgs$options
        if ( is.null(opts$project) ) {
            stop("Missing input files.  Either specify explicit input files or specify the name of the project the `--project' option (`-p' for short).")
        }
        dataFn <-  file.path("results", opts$project, "allData.rdata")
    }
    if (opts$debug) {
        write(sprintf(dataFn, fmt="DEBUG: Reading data from %s"), stderr())
    }
    result <- readRDS(dataFn)
    if (opts$debug) {
        write("DEBUG: Sucessfully read data.", stderr())
    }
    return (result)
}

ssubset <- function(superset, fieldName, cmp, threshold) {
    pfn <- parse(text=fieldName)
    if (cmp == "<") {
        return (subset(superset, eval(pfn) < threshold))
    }
    if (cmp == "<=") {
        return (subset(superset, eval(pfn) <= threshold))
    }
    if (cmp == ">") {
        return (subset(superset, eval(pfn) > threshold))
    }
    if (cmp == ">=") {
        return (subset(superset, eval(pfn) >= threshold))
    }
    stop(paste("Invalid comparison operator:", cmp))
}

mkGrp <- function(subsetBaseName, funcsToSubset, fieldName, cmp, threshold) {
    funcs <- ssubset(funcsToSubset, fieldName, cmp, threshold)
    depName <- sprintf(fieldName,cmp,threshold,fmt="%s%-2s%.3f")
    grp <- c() # dummy to create a fresh object
    grp$name <- paste("f/(", subsetBaseName, " & ", depName, ")", sep="")
    grp$funcs <- funcs
    grp$nrow <- nrow(funcs)
    return (grp)
}

printGrp <- function(grp) {
    write(sprintf(grp$name, grp$nrow, fmt="DEBUG: %s: %d"), stderr())
}

funcsAll <- readData(args)
funcsChanged <- subset(funcsAll, COMMITS > 0)

belowCmp <- "<="
aboveCmp <- ">"
indepBelowCmp <- belowCmp
indepAboveCmp <- aboveCmp
depBelowCmp   <- belowCmp
depAboveCmp   <- aboveCmp

doTheFisher <- function(indep,dep,indepThresh=NULL) {
    ##indep <- "FL"
    ##indepThresh <- 0
    indepNameFmt <- "%s%-2s%d"
    
    ##indep <- "LOC"
    if (is.null(indepThresh)) {
        indepThresh <- median(funcsAll[,indep])
    }
    ##indepNameFmt <- "%s%-2s%.1f"
    
    funcsIndepBelow <- ssubset(funcsAll, indep, indepBelowCmp, indepThresh)
    indepBelowName  <- sprintf(          indep, indepBelowCmp, indepThresh, fmt=indepNameFmt)
    
    funcsIndepAbove <- ssubset(funcsAll, indep, indepAboveCmp, indepThresh)
    indepAboveName  <- sprintf(          indep, indepAboveCmp, indepThresh, fmt=indepNameFmt)
    
    ##dep <- "LCHratio"
    ##depThresh <- quantile(funcsChanged[,dep], c(.5))[1]
    depThresh <- median(funcsChanged[,dep])
    
    depNameFmt <- "%s%-2s%.3f"
    depAboveName <- sprintf(dep, ">" , depThresh, fmt=depNameFmt)
    depBelowName <- sprintf(dep, "<=", depThresh, fmt=depNameFmt)
    
    grpAProne   <- mkGrp(indepAboveName, funcsIndepAbove, dep, depAboveCmp, depThresh)
    grpUProne   <- mkGrp(indepBelowName, funcsIndepBelow, dep, depAboveCmp, depThresh)
    grpAUnprone <- mkGrp(indepAboveName, funcsIndepAbove, dep, depBelowCmp, depThresh)
    grpUUnprone <- mkGrp(indepBelowName, funcsIndepBelow, dep, depBelowCmp, depThresh)
    
    counts <- c(grpAProne$nrow, grpUProne$nrow,
                grpAUnprone$nrow, grpUUnprone$nrow)

    fisherTable <- matrix(counts, nrow=2,
                          dimnames=list(c(indepAboveName, indepBelowName),
                                        c(depAboveName,   depBelowName)))

    if (opts$debug) {
        printGrp(grpAProne)
        printGrp(grpUProne)
        printGrp(grpAUnprone)
        printGrp(grpUUnprone)
        
        write(sprintf(indepAboveName, nrow(funcsIndepAbove), fmt="DEBUG: #f/%s: %10d"),
              stderr())
        write(sprintf(indepBelowName, nrow(funcsIndepBelow), fmt="DEBUG: #f/%s: %10d"),
              stderr())
        write(sprintf(nrow(funcsAll), fmt="DEBUG:       #f: %10d"), stderr())
        print(fisherTable)
    }

    fisherResults <- fisher.test(fisherTable, alternative = "greater")

    OR <- fisherResults$estimate
    p.value  <- fisherResults$p.value
    
    if ( OR > 1 ) {
        if ( p.value < 0.05 ) {
            rating <- "++"
        } else {
            rating <- "+~"
        }
    } else {
        if ( p.value < 0.05 ) {
            rating <- "--"
        } else {
            rating <- "-~"
        }
    }
    
    ##sysname <- basename(dirname(inputFn))
    ##cat(sprintf(opts$smell,rating,OR,p.value,opts$project,fmt="% 3s;%s;%.2f;%.3f;%s\n"))
    ##fisherResults$estimate
    ##fisherResults$p.value

    row <- sprintf(rating,OR,p.value,opts$project
                  ,indep,indepThresh
                  ,dep,depThresh
                  ,fmt="%s,%.2f,%.3f,%s,%s,%.3f,%s,%.3f\n")

    return (row)
}

if ( !opts$no_header ) {
    cat(sprintf(
        indepBelowCmp,indepAboveCmp,
        depBelowCmp,depAboveCmp,
        fmt="Rating,OR,p-value,System,I,%s/%sIthresh,D,%s/%sDthresh\n"))
}

r <- doTheFisher(indep=opts$independent, dep=opts$dependent, indepThresh=opts$ithresh)
cat(r, "\n", sep="")
