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
  , make_option(c("-s", "--sample-size")
              , dest="sample_size"
              , type="integer"
              , default=NA
              , help="Test group differences on a sample of the input data to speed up the computation. [default: use all data]")
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
  , make_option(c("--cores")
              , help="Number of processing cores to use for parallel computation.  On Linux, the number of available CPU cores can be determined with the commands `nproc --all' or `getconf _NPROCESSORS_ONLN'. [default: %default]"
              , default = 4
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

testGroupDifferences <- function(data, indep, dep, indepThresh) {
    data <- data.frame(data)
    data$HAS_CRIT <- data[,indep] > indepThresh

    if (opts$debug) {
        funcsChanged <- subset(data, CHANGED)
        write(sprintf(dep, median(data$COMMITS), mean(data$COMMITS),
                      median(funcsChanged$COMMITS), mean(funcsChanged$COMMITS),
                      fmt="DEBUG: %s averages: median(all), mean(all), median(changed), mean(changed): %.3f, %.3f, %.3f, %.3f"), stderr())
    }

    if (dep == "CHANGED") {
        fisherTable <- table(data$HAS_CRIT, data$CHANGED, dnn=c("annotation", "change_status"))
        colnames(fisherTable) <- c('unchanged', 'changed')
        rownames(fisherTable) <- c('unannotated', 'annotated')

        fisherResults <- fisher.test(fisherTable
                                     ##, alternative = "greater"
                                     )

        p.value  <- fisherResults$p.value
        effectSize <- fisherResults$estimate ## Odds ratio
        magnitude <- ""
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
    }
    
    data.frame(System=opts$project
              ,I=indep
              ,ITh=indepThresh
              ,D=dep
              ,P=p.value ##, significanceCode(p.value)
              ,EffectSize=effectSize)
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

outputResults <- function(resultsDf) {
    system <- unique(resultsDf$System)[1]
    dep <- unique(resultsDf$D)[1]
    magFun <- ifelse(dep=="CHANGED", function(OR) { "" }, cliffMagnitude)
    for (indep in unique(resultsDf$I)) {
        r <- subset(resultsDf, I==indep & D==dep)
        indepThreshold <- unique(r$ITh)[1]
        p.value <- mean(r$P)
        effectSize <- mean(r$EffectSize)
        printf("%15s,%3s,%3d,%8s,%9.3g,%3s,%5.2f,%10s\n"
              ,system
              ,indep,indepThreshold
              ,dep
              ,p.value,significanceCode(p.value)
              ,effectSize,magFun(effectSize)
               )
    }
}

if ( !opts$no_header ) {
    cat(sprintf(
        ##indepBelowCmp,indepAboveCmp,
        ##depBelowCmp,depAboveCmp,
        fmt="System,I,ITh,D,P,PScore,EffectSize,Magnitude\n"))
}

flThresh  <- 0
fcThresh  <- 1
cndThresh <- 0
negThresh <- 0
locThreshold <- median(data$LOC)

fullSize <- nrow(data)
origSampleSize <- fullSize
sampleSize <- origSampleSize
if (!is.na(opts$sample_size)) {
    origSampleSize <- opts$sample_size
    sampleSize <- min(fullSize, origSampleSize)
    if (sampleSize <= 0) {
        msg <- sprintf("Sample size must be a positive integer, not `%d'.", sampleSize)
        stop(msg)
    }
}

sampleSize <- max(1, as.integer(floor(fullSize/max(1, floor(fullSize/sampleSize)))))
numIterations <- max(1, floor(fullSize/sampleSize))

##numIterations <- max(1, floor(fullSize/sampleSize))
##rawNumOmitted <- fullSize %% numIterations
##sampleSizeInc <- floor(rawNumOmitted / numIterations)
##sampleSize <- sampleSize + sampleSizeInc

eprintf("INFO Total number of rows in input data: %d\n", fullSize)
eprintf("INFO Adjusted sample size from %d to: %d\n", origSampleSize, sampleSize)
eprintf("INFO Number of subsets: %d\n", numIterations)
eprintf("INFO Number of omitted rows: %d\n", (fullSize %% sampleSize))

indeps <- list("FL", "FC", "CND", "NEG", "LOC")
numTotalCores <- max(1, opts$cores)
numOuterCores <- min(numIterations, numTotalCores) ##max(1, as.integer(ceiling(numTotalCores / numInnerCores))))
numInnerCores <- length(indeps) ##min(length(indeps), max(1, as.integer(ceiling(numTotalCores / numOuterCores))))
eprintf("DEBUG Number of inner and outer cores: %d, %d\n", numInnerCores, numOuterCores)

remainingIndices <- seq_len(fullSize)
createSampleIndices <- function(sampleNum) {
    eprintf("DEBUG Calculating indices for data chunk %d/%d ...\n", sampleNum, numIterations)
    ##result <- sampleDf(remainingData, sampleSize)
    ##remainingData <<- setdiff(remainingData, result)
    resultIndices <- sample(remainingIndices, size = sampleSize)
    remainingIndices <<- setdiff(remainingIndices, resultIndices)
    ##eprintf("DEBUG Overlap for chunk %d: %d rows\n", sampleNum, nrow(intersect(remainingData, result)))
    ##result$CHUNK_NUM <- sampleNum
    ##eprintf("DEBUG typeof(resultIndices): %s\n", typeof(resultIndices))
    resultIndices
}
listOfChunkIndices <- lapply(seq_len(numIterations), createSampleIndices)
eprintf("DEBUG Done calculating sample indices.\n")

calculateResultsForChunk <- function(chunkNum) {
    chunkIndices <- listOfChunkIndices[[chunkNum]]
    ##eprintf("DEBUG typeof(chunkIndices): %s\n", typeof(chunkIndices))
    ##eprintf("Index: %s\n", paste(chunkIndices))
    eprintf("DEBUG Calculating results for chunk %d/%d (%d rows) ...\n", chunkNum, numIterations, length(chunkIndices))
    dataChunk <- data[chunkIndices, ]
    eprintf("DEBUG Done materializing chunk\n")
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
        testGroupDifferences(dataChunk, indep=indep,  dep=opts$dependent, indepThresh=indepThresh)
    }
    resultsForAllIndeps <- mclapply(indeps, f, mc.cores=numInnerCores)
    Reduce(rbind, resultsForAllIndeps)
}

allResultsList <- mclapply(
    seq_len(numIterations)
  , calculateResultsForChunk
  , mc.cores=numOuterCores
)
eprintf("DEBUG Calculating all %d result.\n", numIterations)

allResultsDf <- Reduce(rbind, allResultsList)
##eprintf("DEBUG Number of rows in results: %d\n", nrow(allResultsDf))
outputResults(allResultsDf)
eprintf("DEBUG Successfully computed group differences for %s in %s.\n",
        unique(allResultsDf$D)[1], unique(allResultsDf$System)[1])
