#!/usr/bin/env Rscript

### Performs linear regression the totals over all snapshots of a system
###
### Input files are the snapshot files under Correlated/

## Example by Fisher:

library(optparse)
##library(methods)
suppressMessages(library(aod))
library(MASS) # for glm.nb

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

significanceCode <- function(p) {
    if (p < 0.0001) { return ("***"); }
    else if (p < 0.001) { return ("**"); }
    else if (p < 0.01) { return ("*"); }
    else if (p < 0.05) { return ("."); }
    else { return (""); }
}

catWaldP <- function(smellName, testRes) {
    p <- waldP(testRes)
    pCode <- significanceCode(p)
    cat(sprintf(smellName, p, pCode, fmt="\n\t% 10s = %.3f %s"))
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
    cat("Signif. codes:  < 0.0001 '***' 0.001 '**' 0.01 '*' 0.05 '.'\n")
}

reportModel <- function(model, modelName) {
    ##print(summary(model))
    print(exp(cbind(OR = coef(model), suppressMessages(confint(model)))))
    cat("Coefficients:\n")
    print(coef(model))
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

tryNbModel <- function (dataFrame, formula, modelName) {
    cat("\n\n")
    cat("***************************\n")
    cat("*** "); cat(modelName); cat(" ***\n")

    model <- glm.nb(formula, data = dataFrame)
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

tryLinearModel2 <- function(indeps, dep, data, controlIndep=NULL) {
    formulaString <- paste(dep, paste(indeps, collapse=" + "), sep=" ~ ")
    formula <- as.formula(formulaString)
    if (is.null(controlIndep)) {
        controlIndep <- indeps[length(indeps)]
    }
    ##modelName <- paste("ifdef + ", controlIndep, " -> ", dep, sep="")
    modelName <- formulaString
    model <- tryLinearModel(data, formula, modelName)
    ##cat("\n")
    ##cat(paste("*** ANOVA of model '", modelName, "' ***\n", sep=""))
    ##print(anova(model, test ="Chisq"))
    ##print(summary(model))
    return (model)
}

tryNbModel2 <- function(indeps, dep, data) {
    formulaString <- paste(dep, paste(indeps, collapse=" + "), sep=" ~ ")
    formula <- as.formula(formulaString)
    modelName <- formulaString
    model <- tryNbModel(data, formula, modelName)
    ##cat("\n")
    ##cat(paste("*** ANOVA of model '", modelName, "' ***\n", sep=""))
    ##print(anova(model, test ="Chisq"))
    ##print(summary(model))
    return (model)
}

plotResiduals <- function(model) {
    ## Taken from https://www.r-bloggers.com/model-validation-interpreting-residual-plots/
    fit <- fitted(model)
    res <- residuals(model)

    qPercent <- 2.5
    q <- qPercent / 100.0

    fitQuantiles <- quantile(x=fit, probs = c(q, 1 - q))
    fitLimits <- c(fitQuantiles[[1]], fitQuantiles[[2]])

    resQuantiles <- quantile(x=res, probs = c(q, 1 - q))
    resLimits <- c(resQuantiles[[1]], resQuantiles[[2]])
    
    plot(x=fit, xlab = "Fitted Values",
         y=res, ylab = "Residuals",
         xlim = fitLimits,
         ylim = resLimits,
         ##log = "x"
         )
    abline(h=0, lty=2)
    lines(smooth.spline(x=fit, y=res
                        ## Smaller nknots values (e.g., 12) make
                        ## spline more smooth, higher ones (e.g., 100) more jagged.
                        
                      , nknots=63
                        )
         ,col="red"
          ,lwd=2
          )
    return (model)
}

sampleDf <- function(df, sz) {
    rowCount <- nrow(df)
    if (rowCount < sz) {
        return (df)
    } else {
        return (df[sample(rowCount, sz), ])
    }
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

##annotationData <- subset(allData, FL > 0)

allData$sqrtLOC <- sqrt(allData$LOC)
allData$logFL <- log(allData$FL + 1)
allData$logFC <- log(allData$FC + 1)
allData$logND <- log(allData$ND + 1)

## Some artificial dependent variables
allData$logLINES_CHANGED <- log(allData$LINES_CHANGED + 1)
allData$sqrtLINES_CHANGED <- sqrt(allData$LINES_CHANGED)

##onlyChanged <- subset(allData, COMMITS > 0)

sampleSize <- 10000
sampleData <- sampleDf(allData, sampleSize)
##sampleData <- allData

indeps <- c(##"logFL", "logFC", "logND"
    ##"FLratio", "FCratio", "NDratio",
    "FL", "FC", "ND",
    ##, "LOACratio",
    "LOC" # last variable is the independent control variable
)
##FLratio + #
##FCratio + #
##NDratio + #
##LOFCratio + #
##logLOAC + #
##logLOFC +

##modelOnSample <- tryLinearModel2(indeps, "logLINES_CHANGED", sampleData)
modelOnSample <- tryNbModel2(indeps, "LINES_CHANGED", sampleData)

##locLinesChangedModelOnSample <- tryLinearModel2(c("logLOC"), "logLINES_CHANGED", sampleData)

##anova(modelOnSample # complex model
##    , locLinesChangedModelOnSample # simple model
##    , test ="Chisq")

##cat(paste("*** LR test of model ***\n", sep=""))
##suppressMessages(library(lmtest))
##lrtest(locLinesChangedModelOnSample, modelOnSample)

### Begin plot creation

outputFn <- paste("Residuals_", "Sample_", opts$project, ".pdf", sep="")
pdf(file=outputFn)
dummy <- plotResiduals(modelOnSample)
##dev.off()
cat(outputFn,"\n",sep="")

##suppressMessages(library(lmtest))
##lrtest(reducedModel, slocModel)

##cat("\n")
##cat("*** Step model ***\n")
##reducedModel <- step(allDataModel, trace=0)
##summary(reducedModel)

