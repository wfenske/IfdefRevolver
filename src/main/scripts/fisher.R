#!/usr/bin/env Rscript

### Performs Fisher's test on the totals over all windows of a system

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
library(effsize)

### Load some common functions
cmdArgs <- commandArgs(trailingOnly = FALSE)
file.arg.name <- "--file="
script.fullname <- sub(file.arg.name, "",
                       cmdArgs[grep(file.arg.name, cmdArgs)])
script.dir <- dirname(script.fullname)
source(file.path(script.dir, "regression-common.R"))

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `<projec-name>/results/joint_data.rds' below the current working directory."
              , default = NULL
                )
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
    description = "Perform Fisher's exact test to determine whether functions fulfilling a particular criterion are changed more likely to be changed than functions not fulfilling that criterion. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

data <- readData(args)
data$CHANGED <- data$COMMITS > 0

doTheFisher <- function(data, indep, dep, indepThresh) {
    df <- data ##data.frame(data)
    df$HAS_CRIT <- df[,indep] > indepThresh

    if (opts$debug) {
        funcsChanged <- subset(df, CHANGED)
        write(sprintf(dep, median(df$COMMITS), mean(df$COMMITS),
                      median(funcsChanged$COMMITS), mean(funcsChanged$COMMITS),
                      fmt="DEBUG: %s averages: median(all), mean(all), median(changed), mean(changed): %.3f, %.3f, %.3f, %.3f"), stderr())
    }

    if (dep == "CHANGED") {
        fisherTable <- table(df$HAS_CRIT, df$CHANGED, dnn=c("annotation", "change_status"))
        colnames(fisherTable) <- c('unchanged', 'changed')
        rownames(fisherTable) <- c('unannotated', 'annotated')

        fisherResults <- fisher.test(fisherTable
                                     ##, alternative = "greater"
                                     )

        p.value  <- fisherResults$p.value
        effectSize <- fisherResults$estimate ## Odds ratio
        magnitude <- ""
    } else {
        tGroup <- subset(df, HAS_CRIT)[,dep]
        cGroup <- subset(df, !HAS_CRIT)[,dep]

        mannWhitneyUResult <- wilcox.test(tGroup, cGroup)
        p.value <- mannWhitneyUResult$p.value
    
        cliffRes <- cliff.delta(tGroup, # treatment group
                                cGroup # control group
                                )
        effectSize <- cliffRes$estimate
        magnitude <- sprintf("%s", cliffRes$magnitude)
    }
    
    row <- sprintf(opts$project
                  ,indep,indepThresh
                  ,dep
                  ,p.value, significanceCode(p.value)
                  ,effectSize, magnitude
                  ,fmt="%15s,%3s,%3d,%8s,%9.3g,%3s,%5.2f,%10s\n")

    return (row)
}

if ( !opts$no_header ) {
    cat(sprintf(
        ##indepBelowCmp,indepAboveCmp,
        ##depBelowCmp,depAboveCmp,
        fmt="System,I,ITh,D,P,EffectSize,Magnitude\n"))
}

##r <- doTheFisher(indep=opts$independent, dep=opts$dependent, indepThresh=opts$ithresh)
##cat(r, "\n", sep="")
pf <- function(...) cat(doTheFisher(...))

flThresh  <- 0
fcThresh  <- 1
ndThresh  <- 0
negThresh <- 0
locThreshold <- median(data$LOC)

for (dep in c("COMMITS", "LCH", "CHANGED")) {
    pf(data, indep="FL",  dep=dep, indepThresh=flThresh)
    pf(data, indep="FC",  dep=dep, indepThresh=fcThresh)
    pf(data, indep="CND", dep=dep, indepThresh=ndThresh)
    pf(data, indep="NEG", dep=dep, indepThresh=negThresh)
    pf(data, indep="LOC", dep=dep, indepThresh=locThreshold)
}
