#!/usr/bin/env Rscript

### Performs linear regression the totals over all snapshots of a system
###
### Input files are the snapshot files under Correlated/

## Example by Fisher:

library(optparse)
##library(methods)
suppressMessages(library(aod))

## Get the P-value of the wald test like this
##
## wald.test(b = coef(mylogit1), Sigma = vcov(mylogit1), Terms = 4:4)$result$chi2["P"]

waldP <- function(testRes) {
    return (testRes$result$chi2["P"])
}

catWaldP <- function(smellName, testRes) {
    cat(sprintf(smellName, waldP(testRes), fmt="\n\t% 10s = %.3f"))
}

checkSignificanceOfIndividualPredictors <- function(model, modelName) {
    cat(sprintf(modelName, fmt="P(> X2) based on Wald test for %s:"))
    termPos <- 2
    for (name in attr(model$terms, "term.labels")) {
        ##print(regTermTest(reducedModel, name))
        ##cat(sprintf(termPos, name, termPos:termPos, fmt="\n%d: %s, %s\n"))
        catWaldP(name, wald.test(b = coef(model), Sigma = vcov(model), Terms = termPos:termPos))
        termPos <- termPos + 1
    }
    cat("\n")
}

reportModel <- function(model, modelName) {
    ##print(summary(model))
    print(exp(cbind(OR = coef(model), suppressMessages(confint(model)))))
    checkSignificanceOfIndividualPredictors(model, modelName)
}

options <- list(
    make_option(c("-s", "--snapshotresultsdir")
              , help="Name of the directory where the snaphsot results are located.  This folder is expected to contain folder named, e.g., `1996-07-01', which, in turn, contain CSV files named `joint_function_ab_smell_snapshot.csv'."
              , default = NULL
                )
    #make_option(c("-f", "--fixes"), type="integer", default=1, metavar="NUM",
                                        #help="Minimum number of bug-fix commits to the file to consider it fault-prone [default %default]")
    , make_option(c("-c", "--changes"), type="integer", default=1, metavar="NUM",
                  help="Minimum number of commits to the file to consider it change-prone [default %default]")
    #, make_option(c("-l", "--large"), type="double", default=30.0, metavar="PERCENT",
    #help="Large files must belong to the given top-x percent, SLOC-wise [default %default, meaning %default%]")
)

args <- parse_args(OptionParser(
    description = "Build a linear regression model to determine which independent variables have a significant effect on functions being (or not) change-prone. If no input files are named, the directory containing the results for all the snapshots must be specified via the `--snapshotsdir' (`-s') option."
  , usage = "%prog [options] [file ...]"
  , option_list=options)
  , positional_arguments = c(0, Inf))
opts <- args$options

getInputFilenames <- function(commandLineArgs) {
    result <- commandLineArgs$args
    if ( length(result) > 0 ) {
        return (result)
    }

    opts <- commandLineArgs$options
    if ( is.null(opts$snapshotresultsdir) ) {
            stop("Missing input files.  Either specify explicit input files or specify the directory containing the snapshot results via the `--snapshotresultsdir' option (`-s' for short).")
    }

    baseDir <- opts$snapshotresultsdir
    snapshotDirs <- list.files(baseDir, pattern="[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]")
    if (length(snapshotDirs) == 0) {
        stop(paste("No snapshot directories found in `", baseDir, "'", sep=""))
    }
    return (lapply(snapshotDirs, function(snapshotDir) { file.path(baseDir, snapshotDir, "joint_function_ab_smell_snapshot.csv") }))
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
                               , "BUGFIXES"="numeric"
                               , "LINE_DELTA"="numeric"
                               , "LINES_DELETED"="numeric"
                               #, "LINES_ADDED"="numeric"
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

tryLogitModel <- function (formula, modelName) {
    if (missing(modelName)) { modelName <- formula }
    cat("\n\n")
    cat("***************************\n")
    cat("*** "); cat(modelName); cat(" ***\n")

    model <- glm(formula, data = data, family = binomial(logit))
    reportModel(model, modelName)
    return(model)
}

tryLinearModel <- function (dataFrame, formula, modelName) {
    if (missing(modelName)) { modelName <- formula }
    cat("\n\n")
    cat("***************************\n")
    cat("*** "); cat(modelName); cat(" ***\n")

    model <- lm(formula, data = dataFrame)
    reportModel(model, modelName)
    return(model)
}

computeOrTable <- function(dep, indep) {
    truePos  <- sum(dep    & indep)
    falseNeg <- sum(dep    & (!indep))
    falsePos <- sum((!dep) & indep)
    trueNeg  <- sum((!dep) & (!indep))
    return(c(truePos, falseNeg, falsePos, trueNeg))
}

## Taken/adapted from http://stackoverflow.com/questions/12572357/precision-recall-and-f-measure-in-r
calcPrecisionRecallFmeasure <- function(predict, actual_labels) {
    precision <- sum(predict & actual_labels) / sum(predict)
    recall <- sum(predict & actual_labels) / sum(actual_labels)
    fmeasure <- 2 * precision * recall / (precision + recall)
    return(c(precision, recall, fmeasure))
}

## TODO: Fix or delete
printOrTable <- function(ort, nameDep, nameIndep) {
    Fsmelly  <- ort[1]
    Fclean   <- ort[2]
    NFsmelly <- ort[3]
    NFclean  <- ort[4]
    cat(sprintf("", nameDep, paste("Not", nameDep, sep=" ")      , fmt="%21s|%13s|%16s\n"))
    cat(sprintf(nameIndep,   Fsmelly, NFsmelly                   , fmt="%21s|%13d|%16d\n"))
    cat(sprintf(paste("Not", nameIndep, sep=" "), Fclean, NFclean, fmt="%21s|%13d|%16d\n"))
}

tryOrs <- function(dep, nameDep, indep, nameIndep) {
    ort <- computeOrTable(dep, indep)
    printOrTable(ort, nameDep, nameIndep)

    orSmelly <- (ort[1]/ort[3]) / (ort[2]/ort[4])
    prf <- calcPrecisionRecallFmeasure(indep, dep)

    cat(sprintf(orSmelly,     fmt="Odds ratio: %5.2f\t"))
    cat(sprintf(prf[1] * 100, fmt="Precision: %5.2f%%\t"))
    cat(sprintf(prf[2] * 100, fmt="Recall: %5.2f%%\t"))
    cat(sprintf(prf[3] * 100, fmt="F-Measure: %5.2f%%\n"))

    cat(sprintf(nameIndep, prf[1], prf[2], prf[3], fmt="%s;%.3f;%.3f;%.3f\n"))
}

### Independent variables for taking smell presence into account

## Independent variables for taking file size into account
##topSLOCValue <- quantile(data$SLOC, 1.0 - opts$large / 100.0)
##data$binLARGE <- data$SLOC > topSLOCValue

### Independent variables
## FUNCTION_LOC,LOAC,LOFC,NOFL,NOFC_Dup,NOFC_NonDup,NONEST

## Some renaming
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

annotationData <- subset(allData, FL > 0)

##nrow(allData)
##nrow(annotationData)

if (FALSE) {
    cat("*** Change-Proneness ***\n")
    tryOrs(data$CHANGE_PRONE, "Change-Prone", data$ANY > 0, "Smelly")
    ##cat("\n")
    ##tryOrs(data$CHANGE_PRONE, "Change-Prone", data$binLARGE, sprintf(opts$large, fmt="SLOC in top-%.1f%%"))
    ##cat("\n")
    ##tryOrs(data$CHANGE_PRONE, "Change-Prone", data$binLARGE | (data$ANY > 0), sprintf(opts$large, fmt="Large||Smelly"))
}


##cat("*** SLOC Model ***\n")
##slocModel <- glm(FAULT_PRONE ~ SLOC
##               , data = data
##               , family = binomial(logit))
##reportModel(slocModel)

##tryLogitModel("FAULT_PRONE ~ SIZE", "Size Only")

##plot(fitted(largeModel), residuals(largeModel),
##     xlab = "Fitted Values", ylab = "Residuals")
##abline(h=0, lty=2)
##lines(smooth.spline(fitted(largeModel), residuals(largeModel)))

##tryLogitModel("FAULT_PRONE ~ AB + AF + LF",        "Individual Smells Only")
##tryLogitModel("FAULT_PRONE ~ SIZE + AB + AF + LF", "Size & Individual Smells")
##tryLogitModel("FAULT_PRONE ~ ANY",                 "Any Smell")
##tryLogitModel("FAULT_PRONE ~ SIZE + ANY",          "Size & Any Smell")

##anova(largeModel, smellAndLargeModel, test ="Chisq")
##
##cat("\n*** ANOVA of Smell Only Model ***\n\n")
##anova(smellOnlyModel, test ="Chisq")
##
#### Try to simplify the model and output the resulting formula
##cat("\n*** Reduced Model ***\n")
##
##reducedModel <- step(smellOnlyModel, trace=0)
##summary(reducedModel)
##
##reducedOrs <- exp(cbind(OR = coef(reducedModel), suppressMessages(confint(reducedModel))))
##reducedOrs
##formula(reducedModel)
##
##cat("\n*** Checking Individual Predictors ***\n")
##
##suppressMessages(library(survey))
##
###cat("\n")
###checkSignificanceOfIndividualPredictors(smellOnlyModel,    "Full Model")
##cat("\n")
##checkSignificanceOfIndividualPredictors(reducedModel, "Reduced Model")
##
##cat("\n*** ANOVA of Reduced Model ***\n\n")
##anova(reducedModel, test ="Chisq")
##
##cat("*** Smell Model ***\n")
##
##tryLinearModel(HUNKS ~ FL + FC + ND + LOACratio,          "#ifdef only -> HUNKS")
##tryLinearModel(HUNKS ~ FL + FC + ND + LOACratio + LOC,    "#ifdef & LOC -> HUNKS")
##tryLinearModel(HUNKS ~ FL + FC + ND + LOACratio + logLOC, "#ifdef & log(LOC) -> HUNKS")
tryLinearModel(allData,
               HUNKS #
               ##LCHratio #
               ~ #
                   FL + #
                   ##FLratio + #
                   FC + #
                   ##FCratio + #
                   ND + #
                   ##NDratio + #
                   LOACratio + #
                   ##LOFCratio + #
                   ##logLOAC + #
                   ##logLOFC +
                   logLOC
             , "all: #ifdef & others -> HUNKS")

tryLinearModel(annotationData,
               HUNKS #
               ##LCHratio #
               ~ #
                   FL + #
                   ##FLratio + #
                   FC + #
                   ##FCratio + #
                   ND + #
                   ##NDratio + #
                   LOACratio + #
                   ##LOFCratio + #
                   ##logLOAC + #
                   ##logLOFC +
                   logLOC #
             , "annotated: #ifdef & others -> HUNKS")

##
##summary(smellModel)
##
##smellOrs <- exp(cbind(OR = coef(smellModel), suppressMessages(confint(smellModel))))
##smellOrs

##cat("\n\n***************************\n")
##cat("*** Checking Goodness of Fit of Large-File Model Against Smell & Large-File Model ***\n\n")
##anova(largeModel, smellAndLargeModel, test ="Chisq")

#anova(smellOnlyModel)
#anova(largeModel)

##suppressMessages(library(lmtest))
##lrtest(reducedModel, slocModel)
