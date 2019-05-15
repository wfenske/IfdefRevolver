#!/usr/bin/env Rscript

### Performs regression over all snapshots of a system

library(optparse)
library(parallel)
suppressMessages(library(aod))
suppressMessages(library(MASS)) # for glm.nb
suppressMessages(library(pscl)) # for Zero-inflated Poisson models

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
  , make_option(c("-a", "--annotated"),
                default=FALSE,
                action="store_true",
                help="Restrict data to only annotated functions. [default: %default]")
  , make_option(c("-c", "--changed"),
                default=FALSE,
                action="store_true",
                help="Restrict data to only changed functions functions. [default: %default]")
  , make_option(c("-b", "--balance"),
                default=FALSE,
                action="store_true",
                help="Balance input data (via downsampling) so that there is an equal amount of annotated and non-annotated functions.  Cannot be used together with option `--annotated'. [default: %default]")
  , make_option(c("-s", "--standardize"),
                default=FALSE,
                action="store_true",
                help="Standardize all independent variables. [default: %default]")
)

args <- parse_args(OptionParser(
    description = "Build a negative binomial regression model to determine which independent variables have a significant effect on functions being (or not) change-prone. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

sysname <- "<unknown>"
if ( ! is.null(opts$project) ) {
    sysname <<- opts$project
}

allData <- readData(args)

## Get the P-value of the wald test like this
##
## wald.test(b = coef(mylogit1), Sigma = vcov(mylogit1), Terms = 4:4)$result$chi2["P"]
##waldP <- function(testRes) {
##    return (testRes$result$chi2["P"])
##}

calculateChiSqStat <- function(modelSummary, modelName) {
    chisqp <- TRUE
    if (is.null(modelSummary$deviance)) {
        eprintf("WARN: Cannot determine model fitness: modelSummary$deviance is missing.\n")
        chisqp <- FALSE
    }
    if (is.null(modelSummary$df.residual)) {
        eprintf("WARN: Cannot determine model fitness: modelSummary$df.residual is missing.\n")
        chisqp <- FALSE
    }
    if (chisqp) {
        chiSqStat <- 1 - pchisq(modelSummary$deviance, modelSummary$df.residual)
        chiSqStatThreshold <- 0.05
        if (chiSqStat > chiSqStatThreshold) {
            judgement <- "fits the data"
        } else {
            judgement <- "does *not* fit the data"
        }
        eprintf("INFO: Model %s %s: Chi-square-test statistic: %.3f (should be > %.3f).\n", modelName, judgement, chiSqStat, chiSqStatThreshold)
        return (chiSqStat)
    } else {
        return (NA)
    }
}

reportModel <- function(model, modelName, mcfadden, csvOut=TRUE, warnings=0, warningMessages="") {
    modelSummary <- summary(model)
    ##print(summary(model))
    ##print(exp(cbind(OR = coef(model), suppressMessages(confint(model)))))
    ##checkSignificanceOfIndividualPredictors(model, modelName)

    if (!csvOut) {
        cat("Coefficients:\n"); print(coef(model))
        coefValues <- modelSummary$coefficients[,"Estimate"]
        pValues <- modelSummary$coefficients[,"Pr(>|z|)"]
        printFirstCol <- function(what) {
            printf("% 10s ", what)
        }
        printFirstCol("IV Name")
        for (l in labels(pValues)) {
            printf("%13s", l)
        }
        printf("\n")
        printFirstCol("Coeff")
        for (c in coefValues) {
            printf(" % 12.9f", c)
        }
        printf("\n")
        printFirstCol("p")
        for (p in pValues) {
            printf(" % 12.9f", p)
        }
        printf("\n")
        printFirstCol("sign. code")
        for (p in pValues) {
            printf("% 13s", significanceCode(p))
        }
        printf("\n")
        dummy <- calculateChiSqStat(modelSummary, modelName)
    } else {
        chisq <- calculateChiSqStat(modelSummary, modelName)
        msCoefs <- modelSummary$coefficients
        ## Name of dependent variable
        terms <- modelSummary$terms
        dName <- as.character(terms[[2]])
        ## Formula of independent variables
        iLabels <- labels(msCoefs)[[1]]
        iFormula <- paste(iLabels[2:length(iLabels)], collapse="+")
        for (i in 1:nrow(msCoefs)) {
            cName <- iLabels[i]
            c <-  msCoefs[i, "Estimate"]
            z <- msCoefs[i, "z value"]
            p <- msCoefs[i, "Pr(>|z|)"]
            ##printf("%s,%7s,% 27s,%7.0f,%.4f,%.2f,%11s,%- 6.4f,%3s,%.4f,%d,\"%s\"\n",
            printf("%s,%s,%s,%.0f,%.4f,%.2f,%s,% .6f,%.2f,%.4f,%s,%d,\"%s\"\n",
                   sysname
                 , dName, iFormula
                 , model$aic, mcfadden, chisq
                 , cName, c, z, p, significanceCode(p)
                 , warnings, warningMessages)
        }
    }

    
}

##tryGlmModel <- function (family, dataFrame, formula, modelName) {
##    cat("\n\n")
##    cat("***************************\n")
##    cat("*** "); cat(modelName); cat(" ***\n")
##
##    model <- glm(formula, data = dataFrame, family = family)
##    reportModel(model, modelName)
##    return(model)
##}
##
##tryLinearModel <- function (dataFrame, formula, modelName) {
##    cat("\n\n")
##    cat("***************************\n")
##    cat("*** "); cat(modelName); cat(" ***\n")
##
##    model <- lm(formula, data = dataFrame)
##    reportModel(model, modelName)
##    return(model)
##}
##
##computeOrTable <- function(dep, indep) {
##    truePos  <- sum(dep    & indep)
##    falseNeg <- sum(dep    & (!indep))
##    falsePos <- sum((!dep) & indep)
##    trueNeg  <- sum((!dep) & (!indep))
##    return(c(truePos, falseNeg, falsePos, trueNeg))
##}
##
#### Taken/adapted from http://stackoverflow.com/questions/12572357/precision-recall-and-f-measure-in-r
##calcPrecisionRecallFmeasure <- function(predict, actual_labels) {
##    precision <- sum(predict & actual_labels) / sum(predict)
##    recall <- sum(predict & actual_labels) / sum(actual_labels)
##    fmeasure <- 2 * precision * recall / (precision + recall)
##    return(c(precision, recall, fmeasure))
##}
##
##tryOrs <- function(dep, nameDep, indep, nameIndep) {
##    ort <- computeOrTable(dep, indep)
##    printOrTable(ort, nameDep, nameIndep)
##
##    orSmelly <- (ort[1]/ort[3]) / (ort[2]/ort[4])
##    prf <- calcPrecisionRecallFmeasure(indep, dep)
##
##    cat(sprintf(orSmelly,     fmt="Odds ratio: %5.2f\t"))
##    cat(sprintf(prf[1] * 100, fmt="Precision: %5.2f%%\t"))
##    cat(sprintf(prf[2] * 100, fmt="Recall: %5.2f%%\t"))
##    cat(sprintf(prf[3] * 100, fmt="F-Measure: %5.2f%%\n"))
##
##    cat(sprintf(nameIndep, prf[1], prf[2], prf[3], fmt="%s;%.3f;%.3f;%.3f\n"))
##}

##tryLinearModel2 <- function(indeps, dep, data) {
##    formulaString <- paste(dep, paste(indeps, collapse=" + "), sep=" ~ ")
##    formula <- as.formula(formulaString)
##    modelName <- paste("linear:", formulaString)
##    model <- tryLinearModel(data, formula, modelName)
##    ##cat("\n")
##    ##cat(paste("*** ANOVA of model '", modelName, "' ***\n", sep=""))
##    ##print(anova(model, test ="Chisq"))
##    ##print(summary(model))
##    return (model)
##}

tryNbModel <- function(indeps, dep, data, csvOut=FALSE) {
    indepsFormula <- paste(indeps, collapse=" + ")
    ## This formula also considers 2-way interactions
    ##formulaString <- sprintf("%s ~ (%s)^2 - 1 ", dep, indepsFormula)
    ## This model considers no interactions
    formulaString <- sprintf("%s ~ %s", dep, indepsFormula)
    formula <- as.formula(formulaString)
    modelName <- paste("negbin:", formulaString)

    eprintf("\nDEBUG: ***************************\n")
    eprintf("DEBUG: *** %s ***\n", modelName)

    numWarnings <- 0
    warnMsg <- NULL
    wHandler <- function(w) {
        eprintf("WARN: %s: %s\n", formulaString, w)
        numWarnings <<- numWarnings + 1
        if (is.null(warnMsg)) {
            warnMsg <<- w
        } else {
            warnMsg <<- paste(warnMsg, w, sep=";")
        }
        invokeRestart("muffleWarning")
    }

    model <- withCallingHandlers(glm.nb(formula, data = data), warning=wHandler)
    nullModel <- glm.nb(as.formula(paste(dep, "1", sep="~"))
                      , data=data
                      , control=glm.control(maxit=100)
                        )

    mcfadden <- mcfaddensPseudoRSquared(model, nullModel)
    
    if (is.null(warnMsg)) {
        warnMsg <- ""
    } else {
        warnMsg <- gsub("[\r\n\t]", " ", warnMsg)
        warnMsg <- gsub("  *", " ", warnMsg)
        warnMsg <- gsub(" $", "", warnMsg)
    }

    if (numWarnings == 0) {
        sWithWarnings <- "without warnings"
    } else {
        if (numWarnings == 1) {
            sWithWarnings <- "with 1 warning"
        } else {
            sWithWarnings <- sprintf("with %d warnings", numWarnings)
        }
    }
    eprintf("INFO: *** N-b regression %s completed %s ***\n",
            formulaString, sWithWarnings)
    
    return (function() reportModel(model, modelName, mcfadden, csvOut=csvOut, warnings=numWarnings, warningMessages=warnMsg))
    
    ##cat("\n")
    ##cat(paste("*** ANOVA of model '", modelName, "' ***\n", sep=""))
    ##print(anova(model, test ="Chisq"))
    ##return (model)
}

##tryGlmModel2 <- function(family, indeps, dep, data) {
##    formulaString <- paste(dep, paste(indeps, collapse=" + "), sep=" ~ ")
##    formula <- as.formula(formulaString)
##    modelName <- paste("glm('", family, "'): ", formulaString, sep="")
##    model <- tryGlmModel(family, data, formula, modelName)
##    ##cat("\n")
##    ##cat(paste("*** ANOVA of model '", modelName, "' ***\n", sep=""))
##    ##print(anova(model, test ="Chisq"))
##    ##print(summary(model))
##    return (model)
##}

## tryZeroInflModel <- function(indeps, dep, data, csvOut=FALSE) {
##     ## See for more information on how to interpret these models:
##     ##
##     ## http://datavoreconsulting.com/programming-tips/count-data-glms-choosing-poisson-negative-binomial-zero-inflated-poisson/
## 
##     indepsStr <- paste(indeps, collapse=" + ")
## 
##     ## Step 1: find out whether there are significantly more zeros
##     ## than expected for a Poisson distribution.
##     ##
##     ## Formula: y ~ x|1.
##     ##
##     ## Probably, the intercept of the zero model is significant if
##     ## there are mode zeros than expected for a Poisson distribution.
##     formulaString <- paste(dep, " ~ ", indepsStr, "|log2AGE + log2LAST_EDIT", sep="")
##     formula <- as.formula(formulaString)
## 
##     ## Step 2: Fit a zero-inflated model to test a treatment effect
##     ## for both the counts and the zeros (with '~ x|x') and check
##     ## whether the probability of zero is significantly different
##     ## between the two.
##     ##
##     ## Formula: y ~ x|x
##     ##
##     ## I don't know how to find that out. :-(
##     ##formulaString2 <- paste(dep, " ~ ", indepsStr, "|", indepsStr, sep="")
##     ##formula2 <- as.formula(formulaString2)
## 
##     ## Step 3: Test for overdispersion in the count part of the
##     ## zero-inflated model by specifying a negative binomial
##     ## distribution.
##     ##
##     ## Formula: y ~ x|1 ... dist="negbin"
##     ##
##     ## If the estimated theta parameter is **not** significant, the
##     ## zero-inflated Poisson model is appropriate.  Otherwise, the
##     ## negative binomial model is appropriate.
##     
##     ## A simple inflation model where all zero counts have the same
##     ## probability of belonging to the zero component can by specified
##     ## by the formula y ~ x1 + x2 | 1.
## 
##     ## If the estimated theta parameter is **not** significant, this
##     ## indicates that the zero-inflated Poisson model is more
##     ## appropriate than the neg-bin model.
## 
##     modelNameNegbin <- paste("zero-inflated negative binomial:", formulaString)
##     eprintf("\n\n")
##     eprintf("***************************\n")
##     eprintf("*** %s ***\n", modelNameNegbin)
##     model.negbin <- zeroinfl(formula, data = data, dist = "negbin")
##     m <- model.negbin
##     mName <- modelNameNegbin
##     ##print(summary(model.negbinIntercept))
##     if (csvOut) {
##         if (csvHeader) {
##             printf("SYSTEM,AIC,DEPENDENT,TERM_COUNT,TERMS\n")
##         }
## 
##         ## p-Values for each coefficient of the count model. The first
##         ## one is the intercept. The values can be accessed via
##         ## `pValues[i]', where `i' is either a 1-based index or a
##         ## name, such as '(Intercept)' or 'FL'.  The last pValue is
##         ## for Log(theta).
##         pValues <- summary(m)$coefficients$count[,"Pr(>|z|)"]
## 
##         ## Other interesting attributes of nb-models:
##         ## > nb.model$converged
##         ## [1] TRUE
##         ## > nb.model$th.warn
##         ## [1] "Grenze der Alternierungen erreicht"
##         
##         printf("%7s,%7.0f,%s,%d,%s\n", sysname, AIC(logLik(m)), dep, length(indeps), indepsStr)
##         ## NOTE: Smaller values of AIC are better.  (Cf. https://ncss-wpengine.netdna-ssl.com/wp-content/themes/ncss/pdf/Procedures/NCSS/Negative_Binomial_Regression.pdf)
##     } else {
##         print(summary(m))
##         reportModel(m, mName, -1, csvOut=csvOut, warnings=0, warningMessages="")
##     }
## 
## 
## ##    if (FALSE) {
## ##    modelName2negbin <- paste("zero-inflated negative binomial:", formulaString2)
## ##    cat("\n\n")
## ##    cat("***************************\n")
## ##    cat("*** "); cat(modelName2negbin); cat(" ***\n")
## ##    model.negbin2 <- zeroinfl(formula2, data = data, dist = "negbin")
## ##    print(summary(model.negbin2))
## ##    }
##     
##     return (model.negbin)
## }

plotResiduals <- function(model) {
    ## Taken from https://www.r-bloggers.com/model-validation-interpreting-residual-plots/
    fit <- fitted(model)
    res <- residuals(model)

    qqnorm(res); qqline(res, col = 2)
    ##qqplot(qnorm(ppoints(length(res))), qnorm(res))

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
         ##pch=16, ## plot dots, not circles
         )
    abline(h=0, lty=2)
    lines(smooth.spline(x=fit, y=res
                        ## Smaller nknots values (e.g., 12) make
                        ## spline more smooth, higher ones (e.g., 100) more jagged.
                        
                      ##, nknots=max(min(42, length(res) %% 10), 4)
                        )
         ,col="red"
         ,lwd=2
          )
    return (model)
}

### Independent variables for taking smell presence into account

## Independent variables for taking file size into account
##topSLOCValue <- quantile(data$SLOC, 1.0 - opts$large / 100.0)
##data$binLARGE <- data$SLOC > topSLOCValue

### Independent variables
## LOC,log2LOC,
## LOAC,log2LOAC,LOACratio,
## LOFC,logLOFC,LOFCratio,
## FL (NOFL), FLratio (FL per LOC)
## FC (from NOFC_NonDup), FCratio (FC per LOC)
## CND (from NONEST), CNDratio (CND per LOC)

### Dependent variables
## HUNKS, HUNKSratio (HUNKS per LOC)
## COMMITS, COMMITSratio (COMMITS per LOC)
## LINES_CHANGED, LCHratio (LINES_CHANGED per LOC)
## LINE_DELTA,
## LINES_DELETED,
## LINES_ADDED

##annotationData <- subset(allData, FL > 0)

##allData$FLratio  <- allData$FL  / allData$LOC
##allData$FCratio  <- allData$FC  / allData$LOC
##allData$CNDratio  <- allData$CND  / allData$LOC
##allData$NEGratio <- allData$NEG / allData$LOC

##allData$sqrtLOC <- sqrt(allData$LOC)
##allData$sqrtFL <- sqrt(allData$FL)
##allData$sqrtFC <- sqrt(allData$FC)
##allData$sqrtCND <- sqrt(allData$CND)

## Some artificial dependent variables
##allData$logLINES_CHANGED <- log(allData$LINES_CHANGED + 1)
##allData$sqrtLINES_CHANGED <- sqrt(allData$LINES_CHANGED)

##allData$LCH <- allData$LINES_CHANGED
##allData$LCHratio <- allData$LCH / allData$LOC

##allData$sqrtLogLINES_CHANGED <- sqrt(allData$logLINES_CHANGED)
##allData$logLogLINES_CHANGED <- log(allData$logLINES_CHANGED)

changedData0 <- subset(allData, COMMITS > 0)
medianLCHratio <- median(changedData0$LCHratio)

eprintf("Median COMMITS/LCHG of changed functions:\n%.2g,%.2g\n"
      , median(changedData0$COMMITS)
      , median(changedData0$LCH))

##cat("Median changed lines/LOC for all changed functions: ", medianLCHratio, "\n", sep="")

allData$CHURN_PRONE <- allData$LCHratio > medianLCHratio

changedData <- subset(allData, COMMITS > 0)

allNRow <- nrow(allData)
allChurnProneNRow <- nrow(subset(allData, CHURN_PRONE))
allChurnPronePercent <- allChurnProneNRow * 100.0 / allNRow
##eprintf("Amount of churn-prone rows among all rows: %.1f%%\n", allChurnPronePercent)

changedNRow <- nrow(changedData)
changedChurnProneNRow <- nrow(subset(changedData, CHURN_PRONE))
changedChurnPronePercent <- changedChurnProneNRow * 100.0 / changedNRow
##eprintf("Amount of churn-prone rows among rows with changes: %.1f%%\n", changedChurnPronePercent)

changedPercent <- nrow(changedData0) * 100 / allNRow
##eprintf("Amount of changed functions among all functions: %.1f%%\n", changedPercent)

##nrow(subset(allData, is.na(LINES_CHANGED) || !is.finite(LINES_CHANGED)))
##nrow(subset(allData, is.na(LOC) || !is.finite(LOC)))
##nrow(subset(allData, is.na(FL) || !is.finite(FL)))
##nrow(subset(allData, is.na(FC) || !is.finite(FC)))
##nrow(subset(allData, is.na(CND) || !is.finite(CND)))

##sampleChangedSize <- 10000
##negBinData <- sampleDf(changedData, sampleChangedSize)
negBinData <- allData

negBinData <- removeNaFunctions(negBinData)

if (opts$annotated & opts$balance) {
    stop("ERROR: Options `--annotated' and `--balance' cannot be used at the same time.")
}

if (opts$annotated) {
    eprintf("DEBUG: Creating models for just the annotated functions.\n")
    negBinData <- subset(negBinData, FL > 0)
}

if (opts$changed) {
    eprintf("DEBUG: Creating models for just the changed functions.\n")
    negBinData <- subset(negBinData, COMMITS > 0)
}

if (opts$balance) {
    negBinData <- balanceAnnotatedAndUnannotatedFunctions(negBinData)
}

if (opts$standardize) {
    negBinData <- standardizeVariables(negBinData)
}

##ziSampleSize <- 10000
##ziData <- sampleDf(allData, ziSampleSize)
##ziData <- allData

## last variable is the independent control variable
##indeps <- c(
    ##"FLratio", "FCratio", "CNDratio",
    ##"FL", "FC", "CND", "LOC"
    ##, "LOACratio",
##    "logFL", "logFC", "logCND", "logLOC"
    ##"sqrtFL", "sqrtFC", "sqrtCND", "sqrtLOC"
##)
##FLratio + #
##FCratio + #
##CNDratio + #
##LOFCratio + #
##logLOAC + #
##logLOFC +

##model.linear.orig <- tryLinearModel2(indeps=c("FL", "FC", "CND", "LOC"), dep="LINES_CHANGED", data=sampleChangedData)
##model.linear.log <- tryLinearModel2(indeps=c("logFL", "logFC", "logCND", "logLOC"), dep="logLINES_CHANGED", data=sampleChangedData)
##modelOnSample <- tryGlmModel2(binomial(link='logit'), indeps, dep="CHURN_PRONE", sampleChangedData)
##model.poisson.orig <- tryGlmModel2("poisson", indeps=c("FL", "FC", "CND", "LOC"), dep="LINES_CHANGED", data=sampleChangedData)
##model.poisson.orig <- tryGlmModel2("poisson", indeps=c("LOC"), dep="LINES_CHANGED", data=sampleChangedData)
##model.poisson.log <- tryGlmModel2("poisson", indeps=c("logFL", "logFC", "logCND", "logLOC"), dep="logLINES_CHANGED", data=sampleChangedData)
##nbIndeps <- c("FL"
##            , "FC"
##            , "CND"
##            , "LOAC"
##            , "LOFC"
##            , "LOC"
##              )
##ziIndeps <- c("FL"
##            , "FC"
##            , "CND"
##            , "LOFC"
##            , "LOC"
##              )
##
##model.nb.COMMITS  <- tryNbModel(indeps=nbIndeps,       dep="COMMITS", data=negBinData)
##model.nb.HUNKS    <- tryNbModel(indeps=nbIndeps,       dep="HUNKS",   data=negBinData)
##model.nb.LCH    <- tryNbModel(indeps=nbIndeps,       dep="LCH",   data=negBinData)

haveHeader <- FALSE
header <- function() {
    if (haveHeader) {
        return (FALSE)
    } else {
        haveHeader <<- TRUE
        return (TRUE)
    }
}

##negbinCsvModel <- function(dep, indeps) {
##    model <- tryNbModel(indeps=indeps, dep=dep, data=negBinData,
##                        csvOut=TRUE, csvHeader=header())
##    return (model)
##}
##
##zeroinflNegbinCsvModel <- function(dep, indeps) {
##    model <- tryZeroInflModel(indeps=indeps, dep=dep, data=ziData,
##                              csvOut=TRUE, csvHeader=header())
##    return (model)
##}

##csvModel <- zeroinflNegbinCsvModel
##csvModel <- negbinCsvModel

options(mc.cores = detectCores())

allModelClosures <- mclapply(
    c(0,1,2,3),
    function(code) {
        if (code %% 2 == 0)
            dep <- "COMMITS"
        else
            dep <- "LCH"
        if (code %/% 2 == 0)
            formula <- FORMULA_REDUCED
        else
            formula <- FORMULA_FULL
        tryNbModel(indeps=formula, dep=dep, data=negBinData, csvOut=TRUE)
})

printf("SYSTEM,D,FORMULA,AIC,MCFADDEN,CHISQ,I,COEF,Z,P,PCODE,WARNINGS,WARNING_MESSAGES\n")
dummy <- lapply(allModelClosures, function(closure) closure())

##model.zip.COMMITS <- tryZeroInflModel(indeps=ziIndeps, dep="COMMITS", data=ziData)

##model.zip.HUNKS   <- tryZeroInflModel(indeps=ziIndeps, dep="HUNKS",   data=ziData)

##model.zip.LCH = tryZeroInflModel(indeps=c(
##                                     ##"FL", "FC",
##                                     "CND"
##                                        #, "LOC"
##                                 ), dep="LINES_CHANGED", data=ziSampleData)

##cat(paste("*** LR test of model ***\n", sep=""))
##suppressMessages(library(lmtest))
##lrtest(locLinesChangedModelOnSample, modelOnSample)

### Begin plot creation


## A linear model in which all independent variables are tranformed by
## a logarithm and the depent variable is, too (i.e.,
## log(LINES_CHANGED) ~ log(FL) + ... + log(LOC)), leads to a QQ plot
## where the lower and the upper part is bent upwards.  This indicates
## a chi-square distribution.
##
## Reference: http://data.library.virginia.edu/understanding-q-q-plots/

##outputFn <- paste("Residuals_", "Sample_", opts$project, ".pdf", sep="")
##pdf(file=outputFn)
##dummy <- plotResiduals(modelOnSample)

##dev.off()
##cat(outputFn,"\n",sep="")

##suppressMessages(library(lmtest))
##lrtest(reducedModel, slocModel)

##cat("\n")
##cat("*** Step model ***\n")
##reducedModel <- step(allDataModel, trace=0)
##summary(reducedModel)

eprintf("INFO: Successfully computed neg-bin regression models for `%s'.\n", sysname)
