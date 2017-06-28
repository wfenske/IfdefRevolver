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

printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

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
  , make_option(c("--dependent-base"),
              , dest="depBase"
              , default="changed",
              , metavar="CHOICE"
              , help="Consider all functions or just the changed ones to calculate the threshold for the dependent variable. Allowed values: `all', `changed'. [default: %default]")
  , make_option(c("--dependent-average"),
              , dest="depAvg"
              , default="median",
              , metavar="CHOICE"
              , help="How to compute the threshold for the dependent variable. Allowed values: `median', `mean', `total'. [default: %default]")
  , make_option(c("-H", "--no-header")
              , dest="no_header"
              , default=FALSE
              , action="store_true"
              , help="Omit header row. [default: %default]")
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

significanceCode <- function(p) {
    if (p < 0.0001) { return ("***"); }
    else if (p < 0.001) { return ("**"); }
    else if (p < 0.01) { return ("*"); }
    else if (p < 0.05) { return ("."); }
    else { return (""); }
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
    if ((fieldName == "COMMITS") |
        (fieldName == "COMMITSratio") |
        (fieldName == "HUNKS") |
        (fieldName == "HUNKSratio") |
        (fieldName == "LCH") |
        (fieldName == "LCHratio")) {
        thresholdFieldName <- sprintf(fmt="MEDIAN_SNAPSHOT_CH_%s", fieldName)
        funcs <- subset(funcsToSubset, eval(parse(text=sprintf(fmt="%s %s %s",
                                                               fieldName, cmp, thresholdFieldName))))
    } #else {
    ##funcs <- ssubset(funcsToSubset, fieldName, cmp, threshold)
    #}
    depName <- sprintf(fieldName,cmp,threshold,fmt="%s%-2s%.3f")
    grp <- c() # dummy to create a fresh object
    grp$name <- paste("f/(", subsetBaseName, " & ", depName, ")", sep="")
    grp$funcs <- funcs
    grp$nrow <- nrow(funcs)
    return (grp)
}

printGrp <- function(grp, numAllChanged=NULL) {
    percentage <- ""
    if (!is.null(numAllChanged)) {
        percentage <- sprintf((grp$nrow * 100.0 / numAllChanged),
                              fmt=" (%02.2f%% of changed functions fulfilling the first criterion)")
    }
    write(sprintf(grp$name, grp$nrow, percentage, fmt="DEBUG: %s: %6d%s"), stderr())
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
    #indepNameFmt <- "%s%-2s%d"
    
    ##indep <- "LOC"
    if (is.null(indepThresh)) {
        indepThresh <- median(funcsAll[,indep])
    }
    indepNameFmt <- "%s%-2s%.1f"
    
    funcsIndepBelow <- ssubset(funcsAll, indep, indepBelowCmp, indepThresh)
    indepBelowName  <- sprintf(          indep, indepBelowCmp, indepThresh, fmt=indepNameFmt)
    
    funcsIndepAbove <- ssubset(funcsAll, indep, indepAboveCmp, indepThresh)
    indepAboveName  <- sprintf(          indep, indepAboveCmp, indepThresh, fmt=indepNameFmt)

    depBase <- NULL
    if (opts$depBase == "changed") {
        ##write("INFO: dependent variable threshold is computed over just the CHANGED functions, not over all functions.", stderr())
        depBase <- funcsChanged
    } else if (opts$depBase == "all") {
        ##write("INFO: dependent variable threshold is computed over ALL functions, not just the changed ones.", stderr())
        depBase <- funcsAll
    } else {
        stop(paste("Invalid value for option `--dependent-base': ", opts$depBase))
    }

    totalAllLoc <- sum(funcsAll$LOC) * 1.0
    totalAllHunks <- sum(funcsAll$HUNKS) * 1.0
    totalAllCommits <- sum(funcsAll$COMMITS) * 1.0
    totalAllLchg <- sum(funcsAll$LINES_CHANGED) * 1.0
    if (opts$debug) {
        write(sprintf(totalAllLoc, (totalAllCommits/totalAllLoc), (totalAllHunks/totalAllLoc), (totalAllLchg/totalAllLoc),
                      fmt="DEBUG: total LOC: %.0f.  Total averages (COMMITS/LOC, HUNKS/LOC, LCHG/LOC): %.4f, %.4f, %.4f"), stderr())
    }
    
    depFunc <- NULL
    if (opts$depAvg == "median") {
        depFunc <- median
    } else if (opts$depAvg == "mean") {
        depFunc <- mean
    } else if (opts$depAvg == "total") {
        depFunc <- function(values) {
            if (dep == "COMMITSratio")
                return (totalAllCommits/totalAllLoc)
            else if (dep == "HUNKSratio")
                return (totalAllHunks/totalAllLoc)
            else if (dep == "LCHratio")
                return (totalAllLchg/totalAllLoc)
            else stop(paste("Cannot use --dependent-average=total with -d", dep))
        }
    } else {
        stop(paste("Invalid value for option `--dependent-average': ", opts$depAvg))
    }
    
    depThresh <- depFunc(depBase[,dep])
    
    if (opts$debug) {
        write(sprintf(dep, median(funcsAll[,dep]), mean(funcsAll[,dep]),
                      median(funcsChanged[,dep]), mean(funcsChanged[,dep]),
                      fmt="DEBUG: %s averages: median(all), mean(all), median(changed), mean(changed): %.3f, %.3f, %.3f, %.3f"), stderr())
    }
    
    depNameFmt <- "%s%-2s%.3f"
    depAboveName <- sprintf(dep, ">" , depThresh, fmt=depNameFmt)
    depBelowName <- sprintf(dep, "<=", depThresh, fmt=depNameFmt)
    
    grpAProne   <- mkGrp(indepAboveName, funcsIndepAbove, dep, depAboveCmp, depThresh)
    grpUProne   <- mkGrp(indepBelowName, funcsIndepBelow, dep, depAboveCmp, depThresh)
    grpAUnprone <- mkGrp(indepAboveName, funcsIndepAbove, dep, depBelowCmp, depThresh)
    grpUUnprone <- mkGrp(indepBelowName, funcsIndepBelow, dep, depBelowCmp, depThresh)
    
###    counts <- c(grpAProne$nrow, grpUProne$nrow,
###                grpAUnprone$nrow, grpUUnprone$nrow)
###
###    fisherTable <- matrix(counts, nrow=2,
###                          dimnames=list(c(indepAboveName, indepBelowName),
###                                        c(depAboveName,   depBelowName)))
    counts <- c(grpAProne$nrow, grpAUnprone$nrow, # col. 1
                grpUProne$nrow, grpUUnprone$nrow  # col. 2
                )

    fisherTable <- matrix(counts, nrow=2,
                          dimnames=list(c(depAboveName,   depBelowName), # row names
                                        c(indepAboveName, indepBelowName) # column names
                                        ))
 

    if (opts$debug) {
        numChangedIndepAbove <- nrow(subset(funcsIndepAbove, COMMITS > 0))
        numChangedIndepBelow <- nrow(subset(funcsIndepBelow, COMMITS > 0))

        printGrp(grpAProne, numAllChanged=numChangedIndepAbove)
        printGrp(grpUProne, numAllChanged=numChangedIndepBelow)
        printGrp(grpAUnprone)
        printGrp(grpUUnprone)
        
        write(sprintf(indepAboveName, nrow(funcsIndepAbove), fmt="DEBUG: #f/%s: %10d"),
              stderr())
        write(sprintf(indepBelowName, nrow(funcsIndepBelow), fmt="DEBUG: #f/%s: %10d"),
              stderr())
        write(sprintf(nrow(funcsAll), fmt="DEBUG:       #f: %10d"), stderr())
        print(fisherTable)
    }

    fisherResults <- fisher.test(fisherTable
                                 ##, alternative = "greater"
                                 )

    OR <- fisherResults$estimate
    fisher.p.value  <- fisherResults$p.value

    chisqRes <- chisq.test(fisherTable)
    
##    if ( OR > 1 ) {
        fisherPRating <- significanceCode(fisher.p.value)
        chisqPRating <- significanceCode(chisqRes$p.value)
##    } else {
##        fisherPRating <- "x"
##        chisqPRating <- "x"
##    }

    
    ##print(str(chisq.test(fisherTable)))
    ##library(lsr)
    ##write(sprintf(cramersV(fisherTable), fmt="Cramer's V (0.1-0.3=weak,0.4-0.5=medium,>0.5=strong): %.2f"), stderr())
    tGroup <- c(grpAProne$funcs[,dep], grpAUnprone$funcs[,dep])
    cGroup <- c(grpUProne$funcs[,dep], grpUUnprone$funcs[,dep])
###    write(sprintf(cohensD(tGroup,  cGroup), # treatment group
###                                        # control group
###                , fmt="Cohen's D (lsr): %.2f"), stderr())
    library(effsize)
    cliffRes <- cliff.delta(tGroup, # treatment group
                        cGroup # control group
                        )
    cliffEff <- cliffRes$estimate
    cliffDescr <- sprintf("%s",cliffRes$magnitude)
    ##print(cliffRes)
    ##print(str(cliffRes))

###    toyByGender <- matrix(c(2,1,7,2,5,3), nrow=3,
###                          dimnames=list(c("Lego", "Puppen", "PCGames"),
###                                        c("Jungen",   "Maedchen"))
###                          )
###    print(toyByGender)
###    print(chisq.test(toyByGender))
###    write(sprintf(cramersV(toyByGender), fmt="DEBUG: Cramer's V (0.1-0.3=weak,0.4-0.5=medium,>0.5=strong): %.2f"), stderr())

    
    ##sysname <- basename(dirname(inputFn))
    ##cat(sprintf(opts$smell,rating,OR,p.value,opts$project,fmt="% 3s;%s;%.2f;%.3f;%s\n"))
    ##fisherResults$estimate
    ##fisherResults$p.value

    row <- sprintf(opts$project,OR
                  ,fisher.p.value,fisherPRating
                  ,chisqRes$p.value,chisqPRating
                  ,cliffEff,cliffDescr
                  ,indep,indepThresh
                  ,dep,depThresh
                  ,fmt="%8s,%5.2f,%9.3g,%3s,%9.3g,%3s,% 2.4f,%10s,%s,%.0f,%s,%.4f\n")

    return (row)
}

if ( !opts$no_header ) {
    cat(sprintf(
        ##indepBelowCmp,indepAboveCmp,
        ##depBelowCmp,depAboveCmp,
        fmt="System,OR,FisherP,FisherPRating,ChisqP,ChisqPRating,CliffD,CliffDMagnitude,I,Ithresh,D,Dthresh\n"))
}

##r <- doTheFisher(indep=opts$independent, dep=opts$dependent, indepThresh=opts$ithresh)
##cat(r, "\n", sep="")
pf <- function(...) cat(doTheFisher(...))

flThresh  <- 0
fcThresh  <- 1
ndThresh  <- 0
negThresh <- 0

## Without LOC adjustment
pf(indep="FL",  dep="COMMITS",      indepThresh=flThresh)
pf(indep="FL",  dep="LCH",          indepThresh=flThresh)

pf(indep="FC",  dep="COMMITS",      indepThresh=fcThresh)
pf(indep="FC",  dep="LCH",          indepThresh=fcThresh)

pf(indep="ND",  dep="COMMITS",      indepThresh=ndThresh)
pf(indep="ND",  dep="LCH",          indepThresh=ndThresh)

pf(indep="NEG", dep="COMMITS",      indepThresh=negThresh)
pf(indep="NEG", dep="LCH",          indepThresh=negThresh)

pf(indep="LOC", dep="COMMITS")
pf(indep="LOC", dep="LCH")

## Scaled to LOC
pf(indep="FL",  dep="COMMITSratio", indepThresh=flThresh)
pf(indep="FL",  dep="LCHratio",     indepThresh=flThresh)

pf(indep="FC",  dep="COMMITSratio", indepThresh=fcThresh)
pf(indep="FC",  dep="LCHratio",     indepThresh=fcThresh)

pf(indep="ND",  dep="COMMITSratio", indepThresh=ndThresh)
pf(indep="ND",  dep="LCHratio",     indepThresh=ndThresh)

pf(indep="NEG", dep="COMMITSratio", indepThresh=negThresh)
pf(indep="NEG", dep="LCHratio",     indepThresh=negThresh)

pf(indep="LOC", dep="COMMITSratio")
pf(indep="LOC", dep="LCHratio")

## Hmm ..., the odds ratios somtimes look huge, but Cramer's V says
## that there's almost no effect.
