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
  , make_option(c("-n", "--normalize"),
                default=FALSE,
                action="store_true",
                help="Normalize counts for each window to the LOC count for the function.")
    , make_option(c("-H", "--no_header"),
                default=FALSE,
                action="store_true",
                help="Omit header row")
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
    cat("DEBUG: Reading data from ", dataFn, "\n", sep="")
    result <- readRDS(dataFn)
    cat("DEBUG: Sucessfully read data.\n")
    return (result)
}

ssubset <- function(superset, fieldName, cmp, threshold) {
    conditionString <- paste(fieldName, cmp, threshold)
    return (subset(superset, eval(parse(text=conditionString))))
}

mkGrp <- function(name, funcsToSubset, fieldName, cmp, threshold) {
    funcs <- ssubset(funcsToSubset, fieldName, cmp, threshold)
    grp <- c() # dummy to create a fresh object
    grp$name <- name
    grp$funcs <- funcs
    grp$scale <- 1
    grp$nrow <- nrow(funcs)
    return (grp)
}

funcsAll <- readData(args)

annotationThresh <- 2
funcsChanged <- subset(funcsAll, COMMITS > 0)

indep <- "FL"

funcsA <- ssubset(funcsAll, indep, ">=", annotationThresh)
funcsU <- ssubset(funcsAll, indep, "<",  annotationThresh)

dep <- "HUNKSratio"
depQuantiles <- quantile(eval(parse(text=paste("funcsChanged", dep,
                                               sep="$"))),
                         c(.5, .75))
depThresh <- depQuantiles[2]

formulaStringDepHigher <- paste(dep, depThresh, sep=" >= ")
formulaStringDepLower  <- paste(dep, depThresh, sep=" < ")

grpAC  <- mkGrp("annotated, change-prone functions", funcsA,
                dep, " >= ", depThresh)
grpUC  <- mkGrp("unannotated, change-prone functions", funcsU,
                dep, " >= ", depThresh)
grpAU  <- mkGrp("annotated, un-change-prone functions", funcsA,
                dep, " < ", depThresh)
grpUU  <- mkGrp("unannotated, un-change-prone functions", funcsU,
                dep, " < ", depThresh)

if ( opts$normalize ) {
    stop("Normalization is currently broken.")
    averageLoc <- function(setOfFunctions) {
        return (mean(setOfFunctions$LOC))
    }

    avgLoc  <- averageLoc(allData)

    grpScale <- function(grp) {
        return (avgLoc / averageLoc(grp$funcs))
    }

    grpAC$scale <- grpScale(grpAC)
    grpUC$scale <- grpScale(grpUC)
    grpAU$scale <- grpScale(grpAU)
    grpUU$scale <- grpScale(grpUU)
}

grpVal <- function(grp) {
    r <- round(grp$nrow * grp$scale)
    ##cat(grp$name, ":", grp$nrow, grp$scale, r, "\n")
    return (r)
}

normalizedCounts <- c(grpVal(grpAC), grpVal(grpUC),
                      grpVal(grpAU), grpVal(grpUU))

fisherTable <- matrix(normalizedCounts, nrow=2,
                      dimnames=list(c("Annotated", "Unannotated"),
                                    c("Change-Prone", "Not Change-Prone")))
fisherTable
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

##if ( !opts$no_header ) {
    cat("Rating","OR","p-value","System\n",sep=",")
##}

##sysname <- basename(dirname(inputFn))
##cat(sprintf(opts$smell,rating,OR,p.value,opts$project,fmt="% 3s;%s;%.2f;%.3f;%s\n"))
cat(sprintf(rating,OR,p.value,opts$project,fmt="%s,%.2f,%.3f,%s\n"))

#fisherResults$estimate
#fisherResults$p.value
