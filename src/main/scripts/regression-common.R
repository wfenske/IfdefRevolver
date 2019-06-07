printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

sampleDf <- function(df, sz) {
    rowCount <- nrow(df)
    if (rowCount < sz) {
        return (df)
    } else {
        return (df[sample(rowCount, sz), ])
    }
}

balanceAnnotatedAndUnannotatedFunctions <- function(df) {
    fAnnotated   <- subset(df, FL > 0)
    fUnannotated <- subset(df, FL == 0)
    nAnnotated   <- nrow(fAnnotated)
    nUnannotated <- nrow(fUnannotated)

    eprintf("DEBUG: Number of annotated and non-annotated functions: %d, %d\n",
                nAnnotated, nUnannotated)
    
    if (nAnnotated < nUnannotated) {
        eprintf("INFO: Downsampling non-annotated functions from %d to %d\n",
                nUnannotated, nAnnotated)
        return (rbind(fAnnotated,
                      sampleDf(fUnannotated, nAnnotated)))
    } else {
        eprintf("INFO: Downsampling annotated functions from %d to %d\n",
                nAnnotated, nUnannotated)
        return (rbind(sampleDf(fAnnotated, nUnannotated),
                      fUnannotated))
    }
}

removeNaFunctions <- function(df) {
    subset(df, !is.na(AGE) && is.finite(AGE) && !is.na(MRC) && is.finite(MRC))
}

mcfaddensPseudoRSquared <- function(model, nullmodel) {
    ## NOTE: Values between 0.2 and 0.4 already indicate a
    ## substantially better model.
    
    ## Value : [0,1)
    return (1-logLik(model)/logLik(nullmodel))
}

significanceCode <- function(p) {
    if (p < 0.0001) { return ("***"); }
    else if (p < 0.001) { return ("**"); }
    else if (p < 0.01) { return ("*"); }
    else if (p < 0.05) { return ("."); }
    else { return (""); }
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
        dataFn <-  file.path(opts$project, "results", "joint_data.rds")
    }
    eprintf("DEBUG: Reading data from %s\n", dataFn)
    result <- readRDS(dataFn)
    eprintf("DEBUG: Sucessfully read data.\n")
    sizePerSnapshotTable <- table(result$SNAPSHOT_DATE)
    ## Total number of functions in the snapshot
    result$TNF <- sizePerSnapshotTable[result$SNAPSHOT_DATE]
    result$log2TNF <- log2(result$TNF + 1)
    
    return (result)
}

csvCleanWarnMsg <- function(warnMsg) {
    if (is.null(warnMsg)) {
        warnMsg <- ""
    } else {
        warnMsg <- gsub("[\r\n\t]", " ", warnMsg)
        warnMsg <- gsub("  *", " ", warnMsg)
        warnMsg <- gsub(" $", "", warnMsg)
    }
    return(warnMsg)
}

##AGE_VAR <- "sqrtAGE"
##MRC_VAR <- "sqrtMRC"
AGE_VAR <- "log2AGE"
MRC_VAR <- "log2MRC"
PC_VAR  <- "log2PC"
TNF_VAR <- "log2TNF" # total number of functions

VARS_CONTROLS            <- c("log2LOC", AGE_VAR, MRC_VAR, PC_VAR, TNF_VAR)
VARS_CPP_UNTRANSFORMED   <- c("FL", "FC", "CND", "NEG", "LOACratio")
VARS_CPP_LOG_TRANSFORMED <- c("log2FL", "log2FC", "log2CND", "log2NEG", "LOACratio")

FORMULA_CONTROLS                 <- VARS_CONTROLS
FORMULA_CPP_UNTRANSFORMED_ONLY   <- VARS_CPP_UNTRANSFORMED
FORMULA_CPP_LOG_TRANSFORMED_ONLY <- VARS_CPP_LOG_TRANSFORMED
FORMULA_FULL_UNTRANSFORMED       <- c(VARS_CPP_UNTRANSFORMED,   VARS_CONTROLS)
FORMULA_FULL_LOG_TRANSFORMED     <- c(VARS_CPP_LOG_TRANSFORMED, VARS_CONTROLS)

MAX_FORMULA_CODE <- 4

getRegressionIndepsByNumber <- function(formulaCode) {
    if (formulaCode == 0)
        return(FORMULA_CONTROLS)
    if (formulaCode == 1)
        return(FORMULA_CPP_UNTRANSFORMED_ONLY)
    if (formulaCode == 2)
        return(FORMULA_CPP_LOG_TRANSFORMED_ONLY)
    if (formulaCode == 3)
        return(FORMULA_FULL_UNTRANSFORMED)
    if (formulaCode == 4)
        return(FORMULA_FULL_LOG_TRANSFORMED)
}

standardizeVariables <- function(df) {
    sdf <- data.frame(df) ## copy original data frame
    allIndeps <- c(VARS_CONTROLS, VARS_CPP_UNTRANSFORMED, VARS_CPP_LOG_TRANSFORMED)
    allUniqueIndeps <- allIndeps[!duplicated(allIndeps)]
    for (var in allUniqueIndeps) {
        eprintf("DEBUG: standardizing independent variable %s\n", var)
        sdf[,var] <- scale(df[,var])
    }
    ##for (var in c("COMMITS", "LCH")) {
    ##    sdf[,var] <- scale(df[,var])
    ##}
    return (sdf)
}
