#!/usr/bin/env Rscript

### Performs regression over all snapshots of a system

library(optparse)
##library(methods)
suppressMessages(library(aod))
suppressMessages(library(MASS)) # for glm.nb
suppressMessages(library(pscl)) # for Zero-inflated Poisson models
##library(jtools) # for summ, see https://cran.r-project.org/web/packages/jtools/vignettes/interactions.html

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `results/<projec-name>/allData.rdata' below the current working directory."
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
  , make_option(c("-T", "--no-test-code"),
                default=FALSE,
                action="store_true",
                dest="noTestCode",
                help="Exclude functions that likely constitute test code. A simple heuristic based on file name and function name is used to identify such functions. [default: %default]")
  , make_option(c("-W", "--exclude-models-with-warnings"),
                default=FALSE,
                action="store_true",
                dest="noModelsWithWarnings",
                help="Exclude models for which warnings occurred. [default: %default]")
  , make_option(c("--p-max"),
                default=0.05,
                dest="pMax",
                help="Exclude independent variables whose p-value is above the specified value. [default: %default]")
)

args <- parse_args(OptionParser(
    description = "Build a negative binomial regression model to determine which independent variables have a significant effect on functions being (or not) change-prone. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

sysname <- "<unknown>"
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
        sysname <<- opts$project
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

calculateChiSqStat <- function(modelSummary) {
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
        eprintf("INFO: Model %s: Chi-square-test statistic: %.3f (should be > %.3f).\n", judgement, chiSqStat, chiSqStatThreshold)
        return (chiSqStat)
    } else {
        return (NA)
    }
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
        dummy <- calculateChiSqStat(modelSummary)
    } else {
        if (csvHeader) {
            ##printf("SYSTEM,D,FORMULA,AIC,MCFADDEN,CHISQ,I,COEF,Z,P,PCODE,WARNINGS,WARNING_MESSAGES\n")
            printf("SYSTEM,D,FORMULA,MCFADDEN,I,OR,COEF,P,PCODE,WARNINGS,WARNING_MESSAGES\n")
        }
        chisq <- calculateChiSqStat(modelSummary)
        msCoefs <- modelSummary$coefficients
        ## Name of dependent variable
        terms <- modelSummary$terms
        dName <- as.character(terms[[2]])
        ## Formula of independent variables
        iLabels <- labels(msCoefs)[[1]]
        iFormula <- paste(iLabels[2:length(iLabels)], collapse="+")
        if (opts$noModelsWithWarnings & (warnings > 0)) {
            eprintf("INFO: Excluding model `%s': %d warning(s) occurred: %s.\n",
                    iFormula, warnings, warningMessages)
        } else {
            for (i in 1:nrow(msCoefs)) {
                cName <- iLabels[i]
                c <-  msCoefs[i, "Estimate"]
                z <- msCoefs[i, "z value"]
                p <- msCoefs[i, "Pr(>|z|)"]
                if (p > opts$pMax) {
                    eprintf("INFO: Omitting dependent variable `%s': p-value too large (%f > %f).\n",
                            cName, p, opts$pMax)
                } else {
                    ##printf("%s,%7s,% 27s,%7.0f,%.4f,%.2f,%11s,%- 6.4f,%3s,%.4f,%d,\"%s\"\n",
###            printf("%s,%s,%s,%.0f,%.4f,%.2f,%s,% .6f,%.2f,%.4f,%s,%d,\"%s\"\n",
###                   sysname
###                 , dName, iFormula
###                 , model$aic, mcfadden, chisq
###                 , cName, c, z, p, significanceCode(p)
###                 , warnings, warningMessages)
                    ##printf("%s,%s,%43s,%.4f,%21s,%.3f,% .6f,%.4f,%3s,%d,\"%s\"\n",
                    printf("%s,%s,%s,%.4f,%s,%.3f,% .6f,%.4f,%3s,%d,\"%s\"\n",
                           sysname
                         , dName, iFormula
                         , mcfadden
                         , cName, exp(c), c, p, significanceCode(p)
                         , warnings, warningMessages)
                }
            }
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
    
##    if (csvOut) {
##        if (csvHeader) {
##            printf("SYSTEM,AIC,MCFADDEN,DEPENDENT,TERM_COUNT,TERMS\n")
##        }
##        coefficients <- model$coefficients
##        ## Names, such as '(Intercept)', 'FL', etc.
##        ##coefficientNames <- labels(coefficients)
##        ## Coefficient values can be accessed via `coefficients[i]',
##        ## with coefficients[1] being the intercept. `i' can be either
##        ## an 1-based index or a names, such as '(Intercept)' or 'FL'.
##
##        ## p-Values for each coefficient. The first one is the
##        ## intercept. The values can be accessed via `pValues[i]',
##        ## where `i' is either a 1-based index or a name, such as
##        ## '(Intercept)' or 'FL'.
##        pValues <- summary(model)$coefficients[,"Pr(>|z|)"]
##
##        ## Other interesting attributes of nb-models:
##        ## > model$converged
##        ## [1] TRUE
##        ## > model$th.warn
##        ## [1] "Grenze der Alternierungen erreicht"
##        
##        printf("%7s,%7.0f,%.4f,%s,%d,%s\n", sysname, model$aic, mcfadden, dep, length(indeps), indepsFormula)
##        ## NOTE: Smaller values of AIC are better.  (Cf. https://ncss-wpengine.netdna-ssl.com/wp-content/themes/ncss/pdf/Procedures/NCSS/Negative_Binomial_Regression.pdf)
##    } else {
##        ##print(summary(model))
    ##    }

    if (is.null(warnMsg)) {
        warnMsg <- ""
    } else {
        warnMsg <- gsub("[\r\n\t]", " ", warnMsg)
        warnMsg <- gsub("  *", " ", warnMsg)
        warnMsg <- gsub(" $", "", warnMsg)
    }
    
    reportModel(model, modelName, mcfadden, csvOut=csvOut,csvHeader=csvHeader, warnings=numWarnings, warningMessages=warnMsg)
    
    ##cat("\n")
    ##cat(paste("*** ANOVA of model '", modelName, "' ***\n", sep=""))
    ##print(anova(model, test ="Chisq"))
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

allData$FLratio  <- allData$FL  / allData$LOC
allData$FCratio  <- allData$FC  / allData$LOC
allData$NDratio  <- allData$ND  / allData$LOC
allData$NEGratio <- allData$NEG / allData$LOC

allData$sqrtLOC <- sqrt(allData$LOC)
allData$sqrtFL <- sqrt(allData$FL)
allData$sqrtFC <- sqrt(allData$FC)
allData$sqrtND <- sqrt(allData$ND)

allData$log2FL  <- log2(allData$FL + 1)
allData$log2FC  <- log2(allData$FC + 1)
allData$log2ND  <- log2(allData$ND + 1)
allData$log2NEG <- log2(allData$NEG + 1)
allData$log2LOACratio <- log2(allData$LOACratio + 1)

allData$log2LOC <- log2(allData$LOC)

## Some artificial dependent variables
allData$logLINES_CHANGED <- log(allData$LINES_CHANGED + 1)
allData$sqrtLINES_CHANGED <- sqrt(allData$LINES_CHANGED)

##allData$sqrtLogLINES_CHANGED <- sqrt(allData$logLINES_CHANGED)
##allData$logLogLINES_CHANGED <- log(allData$logLINES_CHANGED)

changedData0 <- subset(allData, COMMITS > 0)
medianLCHratio <- median(changedData0$LCHratio)

##cat("Median changed lines/LOC for all changed functions: ", medianLCHratio, "\n", sep="")

eprintf("Median COMMITS/HUNKS/LCHG of changed functions:\n%.2g,%.2g,%.2g\n"
      , median(changedData0$COMMITS)
      , median(changedData0$HUNKS)
      , median(changedData0$LCH))

##allData$CHURN_PRONE <- allData$LCHratio > medianLCHratio
allData$LCH_PRONE <- allData$LCH > 0
allData$COMMITS_PRONE <- allData$COMMITS > 0

changedData <- subset(allData, COMMITS > 0)

allNRow <- nrow(allData)

modelData <- allData
if (opts$annotated) {
    eprintf("DEBUG: Creating models for just the annotated functions.\n")
    modelData <- subset(modelData, FL > 0)
}
if (opts$changed) {
    eprintf("DEBUG: Creating models for just the changed functions.\n")
    modelData <- subset(modelData, COMMITS > 0)
}

if (opts$noTestCode) {
    negBinTestData <- subset(modelData, (grepl(" test", FUNCTION_SIGNATURE) | grepl("Test", FUNCTION_SIGNATURE) | grepl("^test", FILE)))
    
    modelData <- subset(modelData, ! (grepl(" test", FUNCTION_SIGNATURE) | grepl("Test", FUNCTION_SIGNATURE) | grepl("^test", FILE)))

    library(effsize)
    
    tGroup <- negBinTestData # only test code
    cGroup <- modelData     # no test code

    for (v in c("COMMITS", "LCH", "FL", "FC", "ND", "NEG", "LOACratio")) {
        mwuResult <- wilcox.test(tGroup[,v], cGroup[,v])
        cliffRes <- cliff.delta(tGroup[,v], cGroup[,v])
        eprintf("DEBUG: Comparing %s of test code and non-test code: delta=%.3f (%s), p=%.3g\n",
                v, cliffRes$estimate, cliffRes$magnitude, mwuResult$p.value)
    }
}

##modelData <- changedData

##ziSampleSize <- 10000
##ziData <- sampleDf(allData, ziSampleSize)
##ziData <- allData

## last variable is the independent control variable
##indeps <- c(
    ##"FLratio", "FCratio", "NDratio",
    ##"FL", "FC", "ND", "LOC"
    ##, "LOACratio",
##    "logFL", "logFC", "logND", "logLOC"
    ##"sqrtFL", "sqrtFC", "sqrtND", "sqrtLOC"
##)
##FLratio + #
##FCratio + #
##NDratio + #
##LOFCratio + #
##logLOAC + #
##logLOFC +

haveHeader <- FALSE
header <- function() {
    if (haveHeader) {
        return (FALSE)
    } else {
        haveHeader <<- TRUE
        return (TRUE)
    }
}

csvModel <- function(dep, indeps, header=FALSE) {
    model <- tryLogitModel(indeps=indeps, dep=dep, data=modelData, csvOut=TRUE, csvHeader=header())
    return (model)
}

indeps <- c("FL", "FC", "ND", "NEG", "LOACratio")
##indeps <- c("log2FL", "log2FC", "log2ND", "log2NEG", "log2LOACratio")
for (dep in c("COMMITS_PRONE"
              ##, "HUNKS"
              ##, "LCH_PRONE"
              )) {
    ##for (indep in indeps) {
    ##    m <- csvModel(dep, c(indep))
    ##}
    dummy <- csvModel(dep, c("log2LOC"))
    for (indep in indeps) {
        m <- csvModel(dep, c("log2LOC", indep))
    }
    m <- csvModel(dep, c("log2LOC", indeps))
}
##dep <- "COMMITS_PRONE"
##dummy <- csvModel(dep, c("log2LOC"))
##dummy <- csvModel(dep, c("log2LOC", "FL", "FC", "ND", "NEG", "LOACratio"))
##dummy <- csvModel(dep, c("log2LOC", "log2FL", "log2FC", "log2ND", "log2NEG", "log2LOACratio"))
##dummy <- csvModel(dep, c("log2LOC", "log2FL", "log2FC", "log2ND", "log2NEG", "LOACratio"))
