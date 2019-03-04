#!/usr/bin/env Rscript

### Performs regression over all snapshots of a system

library(optparse)
##library(methods)
suppressMessages(library(aod))

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `<projec-name>/results/allDataAge.rdata' below the current working directory."
              , default = NULL
                )
  , make_option(c("-a", "--annotated"),
                default=FALSE,
                action="store_true",
                help="Restrict data to only annotated functions. [default: %default]")
  , make_option(c("-T", "--no-test-code"),
                default=FALSE,
                action="store_true",
                dest="noTestCode",
                help="Exclude functions that likely constitute test code. A simple heuristic based on file name and function name is used to identify such functions. [default: %default]")
)

args <- parse_args(OptionParser(
    description = "Build a logistic binomial regression model to determine which independent variables have a significant effect on functions being (or not) change-prone. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

sysname <- "<unknown>"
if ( ! is.null(opts$project) ) {
    sysname <<- opts$project
}

readData <- function(commandLineArgs) {
    fns <- commandLineArgs$args
    if ( length(fns) == 1 ) {
        dataFn <- fns[1]
    } else {
        opts <- commandLineArgs$options
        if ( is.null(opts$project) ) {
            stop("Missing input files.  Either specify explicit input files or specify the name of the project the `--project' option (`-p' for short).")
        }
        dataFn <-  file.path(opts$project, "results", "allDataAge.rdata")
    }
    eprintf("DEBUG: Reading data from %s\n", dataFn)
    result <- readRDS(dataFn)
    eprintf("DEBUG: Sucessfully read data.\n")
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

catWaldP <- function(predictorName, testRes) {
    p <- waldP(testRes)
    pCode <- significanceCode(p)
    printf("\n\t% 10s = %.3f %s", predictorName, p, pCode)
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

mcfaddensPseudoRSquared <- function(model, nullmodel) {
    ## NOTE: Values between 0.2 and 0.4 already indicate a
    ## substantially better model.
    
    ## Value : [0,1)
    return (1-logLik(model)/logLik(nullmodel))
}

reportModel <- function(model, modelName, mcfadden, csvOut=TRUE, csvHeader=TRUE, warnings=0, warningMessages="") {
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
    } else {
        if (csvHeader) {
            printf("SYSTEM,D,FORMULA,AIC,MCFADDEN,I,COEF,OR,Z,P,PCODE,WARNINGS,WARNING_MESSAGES\n")
        }
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
            or <- exp(c)
            z <- msCoefs[i, "z value"]
            p <- msCoefs[i, "Pr(>|z|)"]
            ##printf("%s,%7s,% 27s,%7.0f,%.4f,%.2f,%11s,%- 6.4f,%3s,%.4f,%d,\"%s\"\n",
            printf("%s,%s,%s,%.0f,%.4f,%s,% .6f,%.3f,%.2f,%.4f,%s,%d,\"%s\"\n",
sysname, dName, iFormula
, model$aic, mcfadden
, cName, c, or
, z, p, significanceCode(p)
, warnings, warningMessages)
        }
    }

    
}

tryLogitModel <- function(indeps, dep, data, csvOut=FALSE, csvHeader=FALSE) {
    indepsFormula <- paste(indeps, collapse=" + ") # without interactions
    ##indepsFormula <- paste(indeps, collapse=" * ") # with interactions
    ## This formula also considers 2-way interactions
    ##formulaString <- sprintf("%s ~ (%s)^2 - 1 ", dep, indepsFormula)
    ## This model considers no interactions
    formulaString <- sprintf("%s ~ %s", dep, indepsFormula)
    formula <- as.formula(formulaString)
    modelName <- paste("logit:", formulaString)

    eprintf("\nDEBUG: ***************************\n")
    eprintf("DEBUG: *** %s ***\n", modelName)

    numWarnings <- 0
    warnMsg <- NULL
    wHandler <- function(w) {
        eprintf("WARN: %s\n", w)
        numWarnings <<- numWarnings + 1
        if (is.null(warnMsg)) {
            warnMsg <<- w
        } else {
            warnMsg <<- paste(warnMsg, w, sep=";")
        }
        invokeRestart("muffleWarning")
    }

    model <- withCallingHandlers(glm(formula = formula
                                   , data = data
                                   , family = binomial(link='logit'))
                               , warning=wHandler)
    nullModel <- glm(as.formula(paste(dep, "1", sep="~"))
                   , data=data
                   , family = binomial(link='logit')
                        )

    mcfadden <- mcfaddensPseudoRSquared(model, nullModel)
    
    if (is.null(warnMsg)) {
        warnMsg <- ""
    } else {
        warnMsg <- gsub("[\r\n\t]", " ", warnMsg)
        warnMsg <- gsub("  *", " ", warnMsg)
        warnMsg <- gsub(" $", "", warnMsg)
    }
    
    reportModel(model, modelName, mcfadden, csvOut=csvOut,csvHeader=csvHeader, warnings=numWarnings, warningMessages=warnMsg)
    
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
## CND (from NONEST), CNDratio (CND per LOC)

### Dependent variables
## HUNKS, HUNKSratio (HUNKS per LOC)
## COMMITS, COMMITSratio (COMMITS per LOC)
## LINES_CHANGED, LCHratio (LINES_CHANGED per LOC)
## LINE_DELTA,
## LINES_DELETED,
## LINES_ADDED

##annotationData <- subset(allData, FL > 0)

allData$FLratio  <- allData$FL  / allData$LOC
allData$FCratio  <- allData$FC  / allData$LOC
allData$CNDratio  <- allData$CND  / allData$LOC
allData$NEGratio <- allData$NEG / allData$LOC

allData$sqrtLOC <- sqrt(allData$LOC)
allData$sqrtFL <- sqrt(allData$FL)
allData$sqrtFC <- sqrt(allData$FC)
allData$sqrtCND <- sqrt(allData$CND)

allData$log2FL  <- log2(allData$FL + 1)
allData$log2FC  <- log2(allData$FC + 1)
allData$log2CND  <- log2(allData$CND + 1)
allData$log2NEG <- log2(allData$NEG + 1)

allData$log2LOC <- log2(allData$LOC)

allData$log2AGE <- log2(allData$AGE + 1)
allData$log2LAST_EDIT <- log2(allData$LAST_EDIT + 1)

## Some artificial dependent variables
allData$logLINES_CHANGED <- log(allData$LINES_CHANGED + 1)
allData$sqrtLINES_CHANGED <- sqrt(allData$LINES_CHANGED)

allData$LCH <- allData$LINES_CHANGED
allData$LCHratio <- allData$LCH / allData$LOC

##allData$sqrtLogLINES_CHANGED <- sqrt(allData$logLINES_CHANGED)
##allData$logLogLINES_CHANGED <- log(allData$logLINES_CHANGED)

changedData0 <- subset(allData, COMMITS > 0)
medianLCHratio <- median(changedData0$LCHratio)

eprintf("Median COMMITS/LCHG of changed functions:\n%.2g,%.2g\n"
      , median(changedData0$COMMITS)
      , median(changedData0$LCH))

##cat("Median changed lines/LOC for all changed functions: ", medianLCHratio, "\n", sep="")

allData$CHANGE_PRONE <- allData$COMMITS > 0

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
modelData <- subset(allData, !is.na(AGE) && is.finite(AGE) && !is.na(LAST_EDIT) && is.finite(LAST_EDIT))

if (opts$annotated) {
    eprintf("DEBUG: Creating models for just the annotated functions.\n")
    modelData <- subset(modelData, FL > 0)
}

if (opts$noTestCode) {
    modelDataTests <- subset(modelData, (grepl(" test", FUNCTION_SIGNATURE) | grepl("Test", FUNCTION_SIGNATURE) | grepl("^test", FILE)))
    
    modelData <- subset(modelData, ! (grepl(" test", FUNCTION_SIGNATURE) | grepl("Test", FUNCTION_SIGNATURE) | grepl("^test", FILE)))

    library(effsize)
    
    tGroup <- modelDataTests # only test code
    cGroup <- modelData # no test code

    for (v in c("COMMITS", "LCH", "FL", "FC", "CND", "NEG", "LOACratio")) {
        mwuResult <- wilcox.test(tGroup[,v], cGroup[,v])
        cliffRes <- cliff.delta(tGroup[,v], cGroup[,v])
        eprintf("DEBUG: Comparing %s of test code and non-test code: delta=%.3f (%s), p=%.3g\n",
                v, cliffRes$estimate, cliffRes$magnitude, mwuResult$p.value)
    }
}

haveHeader <- FALSE
header <- function() {
    if (haveHeader) {
        return (FALSE)
    } else {
        haveHeader <<- TRUE
        return (TRUE)
    }
}

logitCsvModel <- function(dep, indeps) {
    model <- tryLogitModel(indeps=indeps, dep=dep, data=modelData, csvOut=TRUE, csvHeader=header())
    return (model)
}

dep <- "CHANGE_PRONE"
csvModel <- logitCsvModel

##ageVar <- "log2AGE"
ageVar <- "AGE"

dummy <- csvModel(dep, c("log2LAST_EDIT", ageVar, "log2PREVIOUS_COMMITS"))
dummy <- csvModel(dep, c("log2LOC", "log2LAST_EDIT", ageVar, "log2PREVIOUS_COMMITS"))
dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio"))
dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio", "log2LOC"))
dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio", "log2LOC", "log2LAST_EDIT", ageVar, "log2PREVIOUS_COMMITS"))

dummy <- csvModel(dep, c("log2LAST_EDIT", "log2PREVIOUS_COMMITS"))
dummy <- csvModel(dep, c("log2LOC", "log2LAST_EDIT", "log2PREVIOUS_COMMITS"))
dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio"))
dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio", "log2LOC"))
dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio", "log2LOC", "log2LAST_EDIT", "log2PREVIOUS_COMMITS"))
