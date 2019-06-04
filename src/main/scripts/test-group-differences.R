#!/usr/bin/env Rscript

library(optparse)
library(effsize)
library(parallel)
##suppressMessages(library(dplyr))

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
  , make_option(c("-d", "--dependent")
              , default="CHANGED"
              , help="Name of the dependent variable.  Must be one of {CHANGED,COMMITS,LCH} [default: %default]")
  , make_option(c("-f", "--fold-size")
              , dest="fold_size"
              , type="integer"
              , default=NA
              , help="Test group differences on partitions of the input data and aggregate the results to speed up the computation. [default: use all data]")
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
  , make_option(c("-c", "--cores")
              , help="Number of processing cores to use for parallel computation.  On Linux, the number of available CPU cores can be determined with the commands `nproc --all' or `getconf _NPROCESSORS_ONLN'. [default: automatically detect number of available cores and use them all]"
              , type="integer"
              , default = NA
                )
)

args <- parse_args(OptionParser(
    description = "Test for statistically significant group differences in the value of a dependent variable depending on a binary property of preprocessor annotation use. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

data <- readData(args)
data$CHANGED <- data$COMMITS > 0
OUTCOME_IS_CHANGED <- (opts$dependent == "CHANGED")

testGroupDifferences <- function(data, indep, dep, indepThresh) {
    data <- data.frame(data)
    data$HAS_CRIT <- data[,indep] > indepThresh

    if (opts$debug) {
        funcsChanged <- subset(data, CHANGED)
        write(sprintf(dep, median(data$COMMITS), mean(data$COMMITS),
                      median(funcsChanged$COMMITS), mean(funcsChanged$COMMITS),
                      fmt="DEBUG: %s averages: median(all), mean(all), median(changed), mean(changed): %.3f, %.3f, %.3f, %.3f"), stderr())
    }

    if (OUTCOME_IS_CHANGED) {
        fisherTable <- table(data$HAS_CRIT, data$CHANGED, dnn=c("annotation", "change_status"))
        colnames(fisherTable) <- c('unchanged', 'changed')
        rownames(fisherTable) <- c('unannotated', 'annotated')

        fisherResults <- fisher.test(fisherTable
                                     ##, alternative = "greater"
                                     )

        p.value  <- fisherResults$p.value
        effectSize <- fisherResults$estimate ## Odds ratio
        magnitude <- ""

        ## row first, quoted column name second, and case does matter
        ##
        ## row is not annotation / annotated
        ##
        ## column is not changed / changed
        ev.trt <- fisherTable['annotated', 'changed']
        n.trt <- sum(fisherTable['annotated',]) # all annotated

        ev.ctrl <- fisherTable['unannotated', 'changed']
        n.ctrl <- sum(fisherTable['unannotated',]) # all un-annotated
    
        data.frame(System=opts$project
                  ,I=indep
                  ,ITh=indepThresh
                  ,D=dep
                  ,P=p.value ##, significanceCode(p.value)
                  ,EffectSize=effectSize
                  ,ev.trt=ev.trt, n.trt=n.trt
                  ,ev.ctrl=ev.ctrl, n.ctrl=n.ctrl)
    } else {
        tGroup <- subset(data, HAS_CRIT)[,dep]
        cGroup <- subset(data, !HAS_CRIT)[,dep]

        mannWhitneyUResult <- wilcox.test(tGroup, cGroup)
        p.value <- mannWhitneyUResult$p.value
    
        cliffRes <- cliff.delta(tGroup, # treatment group
                                cGroup # control group
                                )
        effectSize <- cliffRes$estimate
        magnitude <- sprintf("%s", cliffRes$magnitude)
    
        data.frame(System=opts$project
                  ,I=indep
                  ,ITh=indepThresh
                  ,D=dep
                  ,P=p.value ##, significanceCode(p.value)
                  ,EffectSize=effectSize)
    }
}

cliffMagnitude <- function(cliffsD) {
    ## The magnitude is assessed using the thresholds provided in (Romano 2006), i.e. |d|<0.147 "negligible",
    ##|d|<0.33 "small", |d|<0.474 "medium", otherwise "large"
    absD <- abs(cliffsD)
    ifelse(absD < 0.147, "negligible",
    ifelse(absD < 0.33, "small",
    ifelse(absD < 0.474, "medium",
           "large")))
}

printHeader <- function() {
    ALL_FIELDS <- c("System", "I", "ITh", "D", "P", "PScore", "EffectSize",
                    "ev.trt", "n.trt", "ev.ctrl", "n.ctrl", # only for Fisher's test
                    "SDEffectSize", "Magnitude", "SDP" # only for Cliff's delta
                    )
    ##METRIC_ONLY_FIELDS <- c("SDP", "SDEffectSize", "Magnitude")
    ##BINARY_ONLY_FIELDS <- c("ev.trt", "n.trt", "ev.ctrl", "n.ctrl")
    ##if (OUTCOME_IS_CHANGED) {
    ##    headerFields <- ALL_FIELDS[!ALL_FIELDS %in% METRIC_ONLY_FIELDS]
    ##} else {
    ##    headerFields <- ALL_FIELDS[!ALL_FIELDS %in% BINARY_ONLY_FIELDS]
    ##}
    printf("%s\n", paste(ALL_FIELDS, collapse=','))
}

outputBinaryResults <- function(resultsDf) {
    system <- unique(resultsDf$System)[1]
    dep <- unique(resultsDf$D)[1]
    for (indep in unique(resultsDf$I)) {
        r <- subset(resultsDf, I==indep & D==dep)
        if (nrow(r) > 1) {
            stop("Cannot have multiple rows when outcome is binary.")
        }
        indepThreshold <- unique(r$ITh)[1]
        p.value <- r$P
        ##      Sys  I   ITh D   P     PC  OR    ev.trt,n.trt,ev.ctrl,n.ctrl
        printf("%15s,%3s,%3d,%8s,%9.3g,%3s,%5.2f,%d,%d,%d,%d,,,\n"
              ,system
              ,indep,indepThreshold
              ,dep
              ,p.value,significanceCode(p.value)
              ,r$EffectSize
              ,r$ev.trt,r$n.trt,r$ev.ctrl,r$n.ctrl
               )
    }
}

outputMetricResults <- function(resultsDf) {
    system <- unique(resultsDf$System)[1]
    dep <- unique(resultsDf$D)[1]
    for (indep in unique(resultsDf$I)) {
        r <- subset(resultsDf, I==indep & D==dep)
        getSd <- ifelse(nrow(r) > 1, sd, function(v) 0.0)
        indepThreshold <- unique(r$ITh)[1]
        p.value <- mean(r$P)
        sd.p.value <- getSd(r$P)
        effectSize <- mean(r$EffectSize)
        sd.effectSize <- getSd(r$EffectSize)
        ##      Sys  I   ITh D   P     PC  E         SD(E),Mag. SD(P)
        printf("%15s,%3s,%3d,%8s,%9.3g,%3s,%5.2f,,,,,%9.3g,%10s,%9.3g\n"
              ,system
              ,indep,indepThreshold
              ,dep
              ,p.value,significanceCode(p.value)
              ,effectSize,sd.effectSize,cliffMagnitude(effectSize)
              ,sd.p.value
               )
    }
}

if ( !opts$no_header ) {
    printHeader()
}

flThresh  <- 0
fcThresh  <- 1
cndThresh <- 0
negThresh <- 0
locThreshold <- median(data$LOC)

fullSize <- nrow(data)

if (OUTCOME_IS_CHANGED || is.na(opts$fold_size)) {
    origFoldSize <- fullSize
    foldSize <- origFoldSize
    numFolds <- 1
} else {
    origFoldSize <- opts$fold_size
    foldSize <- min(fullSize, origFoldSize)
    if (foldSize <= 0) {
        msg <- sprintf("Sample size must be a positive integer, not `%d'.", foldSize)
        stop(msg)
    }
    
    foldSize <- max(1, as.integer(floor(fullSize/max(1, floor(fullSize/foldSize)))))
    numFolds <- max(1, floor(fullSize/foldSize))
}

##numFolds <- max(1, floor(fullSize/foldSize))
##rawNumOmitted <- fullSize %% numFolds
##foldSizeInc <- floor(rawNumOmitted / numFolds)
##foldSize <- foldSize + foldSizeInc

eprintf("INFO Total number of rows in input data: %d\n", fullSize)
eprintf("INFO Adjusted fold size from %d to: %d\n", origFoldSize, foldSize)
eprintf("INFO Number of folds: %d\n", numFolds)
eprintf("INFO Number of omitted rows: %d\n", (fullSize %% foldSize))

if (is.na(opts$cores)) {
    options(mc.cores = detectCores())
} else {
    options(mc.cores = opts$cores)
}
numCores <- getOption("mc.cores")
eprintf("DEBUG Maximum number of cores in use: %d\n", numCores)

folds <- split(sample(fullSize, foldSize * numFolds, replace=F),
               as.factor(1:numFolds))
eprintf("DEBUG Done calculating sample indices.\n")

indeps <- list("FL", "FC", "CND", "NEG", "LOC")
calculateResultsForFold <- function(foldNum) {
    foldIndices <- folds[[foldNum]]
    ##eprintf("DEBUG typeof(foldIndices): %s\n", typeof(foldIndices))
    ##eprintf("Index: %s\n", paste(foldIndices))
    eprintf("DEBUG Processing fold %d/%d (%d rows) ...\n", foldNum, numFolds, length(foldIndices))
    dataFold <- data[foldIndices, ]
    ##if (numCores > 1) {
    ##    remove(data)
    ##}
    ##eprintf("DEBUG Done materializing fold\n")
    f <- function(indep) {
        ##eprintf("DEBUG indep=%s\n", indep)
        indepThresh <- ifelse(indep=="FL"
                             ,flThresh
                             ,ifelse(indep=="FC"
                                    ,fcThresh
                                    ,ifelse(indep=="CND"
                                           ,cndThresh
                                           ,ifelse(indep=="NEG"
                                                  ,negThresh
                                                  ,ifelse(indep=="LOC"
                                                         ,locThreshold
                                                         ,stop(indep))))))
        ##eprintf("DEBUG indepThresh=%f\n", as.double(indepThresh))
        r <- testGroupDifferences(dataFold, indep=indep,  dep=opts$dependent, indepThresh=indepThresh)
        eprintf("DEBUG Done processing %s in fold %d/%d\n", indep, foldNum, numFolds)
        r
    }
    resultsForAllIndeps <- mclapply(indeps, f)
    eprintf("DEBUG Done processing fold %d/%d.\n", foldNum, numFolds)
    Reduce(rbind, resultsForAllIndeps)
}

allResultsList <- mclapply(seq_len(numFolds), calculateResultsForFold)
eprintf("DEBUG Calculating all %d result.\n", numFolds)

allResultsDf <- Reduce(rbind, allResultsList)
##eprintf("DEBUG Number of rows in results: %d\n", nrow(allResultsDf))
if (OUTCOME_IS_CHANGED) {
    outputBinaryResults(allResultsDf)
} else {
    outputMetricResults(allResultsDf)
}
eprintf("DEBUG Successfully computed group differences for %s in %s.\n",
        unique(allResultsDf$D)[1], unique(allResultsDf$System)[1])
