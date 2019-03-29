#!/usr/bin/env Rscript

### Performs regression over all snapshots of a system

library(optparse)
##library(methods)
suppressMessages(library(aod))

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
                help="Restrict data to only annotated functions.  Cannot be used together with option `--balance'. [default: %default]")
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
    description = "Build a logistic binomial regression model to determine which independent variables have a significant effect on functions being change-prone (or not). If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

sysname <- "<unknown>"
if ( ! is.null(opts$project) ) {
    sysname <<- opts$project
}

allData <- readData(args)

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

##annotationData <- subset(allData, FL > 0)

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
modelData <- removeNaFunctions(allData)

if (opts$annotated & opts$balance) {
    stop("ERROR: Options `--annotated' and `--balance' cannot be used at the same time.")
}

if (opts$annotated) {
    eprintf("DEBUG: Creating models for just the annotated functions.\n")
    modelData <- subset(modelData, FL > 0)
}

if (opts$balance) {
    modelData <- balanceAnnotatedAndUnannotatedFunctions(modelData)
}

if (opts$standardize) {
    modelData <- standardizeVariables(modelData)
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
    model <- tryLogitModel(indeps=indeps, dep=dep, data=modelData,
                           csvOut=TRUE, csvHeader=header())
    return (model)
}


csvModel <- logitCsvModel

for (dep in c("CHANGE_PRONE")) { 
    ##dummy <- csvModel(dep, c("log2LOC"))
    dummy <- csvModel(dep, FORMULA_REDUCED)
    ##dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio"))
    ##dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio", "log2LOC"))
    dummy <- csvModel(dep, FORMULA_FULL)

    ##dummy <- csvModel(dep, c(mrcVar, pcVar))
    ##dummy <- csvModel(dep, c("log2LOC", mrcVar, pcVar))
    ##dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio"))
    ##dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio", "log2LOC"))
    ##dummy <- csvModel(dep, c("FL", "FC", "CND", "NEG", "LOACratio", "log2LOC", mrcVar, pcVar))
}

eprintf("INFO: Successfully computed logistic regression models for `%s'.\n", sysname)
