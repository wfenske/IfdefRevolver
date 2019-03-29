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
    return (result)
}

##AGE_VAR <- "sqrtAGE"
##MRC_VAR <- "sqrtMRC"
AGE_VAR <- "log2AGE"
MRC_VAR <- "log2MRC"
PC_VAR  <- "log2PC"

FORMULA_REDUCED <- c("log2LOC", AGE_VAR, MRC_VAR, PC_VAR)
FORMULA_FULL    <- c("FL", "FC", "CND", "NEG", "LOACratio", "log2LOC"
                   , AGE_VAR
                   , MRC_VAR
                   , PC_VAR
                     )

standardizeVariables <- function(df) {
    sdf <- data.frame(df) ## copy original data frame
    for (var in FORMULA_FULL) {
        sdf[,var] <- scale(df[,var])
    }
    ##for (var in c("COMMITS", "LCH")) {
    ##    sdf[,var] <- scale(df[,var])
    ##}
    return (sdf)
}
