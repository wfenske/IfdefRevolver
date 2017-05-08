#!/usr/bin/env Rscript

### Performs linear regression the totals over all snapshots of a system
###
### Input files are the snapshot files under Correlated/

## Example by Fisher:

library(optparse)
##library(methods)
suppressMessages(library(aod))

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `results/<projec-name>/allData.rdata' below the current working directory."
              , default = NULL
                )
)

args <- parse_args(OptionParser(
    description = "Build a linear regression model to determine which independent variables have a significant effect on functions being (or not) change-prone. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
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

allData <- readData(args)

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
## LOC,logLOC,
## LOAC,logLOAC,LOACratio,
## LOFC,logLOFC,LOFCratio,
## FL (NOFL), FLratio (FL per LOC)
## FC (from NOFC_NonDup), FCratio (FC per LOC)
## ND (from NONEST), NDratio (ND per LOC)

### Dependent variables
## HUNKS, HUNKSratio (HUNKS per LOC)
## COMMITS, COMMITSratio (COMMITS per LOC)
## LINES_CHANGED, LCHratio (LINES_CHANGED per LOC)
## LINE_DELTA,
## LINES_DELETED,
## LINES_ADDED

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
