#!/usr/bin/env Rscript

library(optparse)
library(ggplot2)

eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

options <- list(
    make_option(c("-o", "--output")
              , help="Name of output file."
              , default = NULL
                )
  , make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `results/<projec-name>/allData.rdata' below the current working directory."
              , default = NULL
                )
  , make_option(c("-i", "--independent")
              , help="Name of independent variable.  Valid values are: FL (a.k.a. NOFL), FC (a.k.a. NOFC_NonDup), CND, LOAC, LOFC. [default: %default]"
              , default="FL"
                )
  , make_option(c("-d", "--dependent")
                , help="Name of independent variable.  Valid values are, e.g.: HUNKS, COMMITS, LINES_CHANGED. [default: %default]"
                , default="COMMITS"
                )
##  , make_option(c("-s", "--scale")
##              , help="Factor by which the dependent variable should be scaled.  For instance, if --dependent=COMMITS and --scale=LOC, then the dependent variable will be COMMITS/LOC, i.e., the frequency of bug-fixes per lines of code.  Valid values are: LOC (lines per code in the function), COUNT (number of functions), none (take the value of the dependent variable as is). [default: %default]"
##                , default="LOC"
    ##                )

  , make_option(c("--ilim")
                , help="Lower limit to the frequency of occurrence of the independent variable values (specified via `-i') to be included.  Given as a percentage.  This option basically controls the lenght of the tail in case the distribution of the variable has a long tail.  [default: %default]"
                , type = "numeric",
                , default = 0.05
                )

  , make_option(c("--dlim")
                , help="Like `--ilim', but for the dependent variable.  [default: %default]"
                , type = "numeric",
                , default = 0.5
                )
    
  , make_option(c("-n", "--systemname")
              , default=NULL
              , help="Name of the system; will be guessed from the input filenames if omitted"
                )
  , make_option(c("-Y", "--noyaxislabels")
              , action="store_true"
              , default=FALSE
              , help="Omit name and labels of y axis [default %default]"
                )
##  , make_option("--ymax"
##              , default=NULL
##              , type="double"
##              , help="Upper limit for values on the Y-axis.  Limits are determined automatically if not supplied."
##                )
    , make_option(c("-c", "--changed")
                , help="Consider only the values of changed functions. [default: %default]"
                , action = "store_true"
                , default = FALSE
                )
    
    , make_option(c("-a", "--annotated")
                , help="Whether to consider only annotated function or only un-annotated functions or both. [default: %default]"
                , default = "both"
                , metavar = "{yes,no,both}"
                )
  , make_option(c("--debug")
              , help="Print some diagnostic messages. [default: %default]"
              , default = FALSE
              , action="store_true"
                )
)

args <- parse_args(OptionParser(
    description = "Plot of some dependent variable (e.g., number of commits to a function) over an independent variable (e.g., number of feature locations). The project whose data to analyze is specified via the `--project' (`-p') option."
  , usage = "%prog [options]... [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))

opts <- args$options

readData <- function(commandLineArgs) {
    opts <- commandLineArgs$options
    if ( is.null(opts$project) ) {
        stop("Specify the name of the project the `--project' option (`-p' for short).")
    }
    dataFn <-  file.path("results", opts$project, "allData.rdata")
    if (opts$debug) {
        write(sprintf(dataFn, fmt="DEBUG: Reading data from %s"), stderr())
    }
    result <- readRDS(dataFn)
    if (opts$debug) {
        write("DEBUG: Sucessfully read data.", stderr())
    }
    return (result)
}

if ( is.null(opts$systemname) ) {
    systemname <- opts$project
} else {
    systemname <- opts$systemname
}

if ( !is.null(opts$output) ) {
    outputFn <- opts$output
} else {
    outputFn <- paste("correlation-", opts$independent, "-", opts$dependent, "-", systemname, ".pdf", sep="")
}

xAxisName <- opts$independent
##yLabels <- seq(0, 1.0, 0.25)
##axisNameFontScale <- 1.5
##plotNameFontScale <- 1.75

## Return a sample of the supplied data frame
sampleDf <- function(df, sz) {
    rowCount <- nrow(df)
    if (rowCount < sz) {
        return (df)
    } else {
        return (df[sample(rowCount, sz), ])
    }
}

varSymbolExpr <- function(varName) {
    return (parse(text=(varName)))
}

applyRelLim <- function(baseData, varName, minLim) {
    values <- baseData[, varName]
    threshold <- quantile(values, c((100.0 - minLim) / 100.0))[1]
    return (subset(baseData, eval(varSymbolExpr(varName)) <= threshold))
}

##parseScaleOptValue <- function(value) {
##    if ( value == "LOC" ) {
##        computeScaleBy <<- function(df, indepValue, includeBiggerP) {
##            if (includeBiggerP) {
##                dfSubset <- subset(df, INDEPV >= indepValue)
##            } else {
##                dfSubset <- subset(df, INDEPV == indepValue)
##            }
##            return (sum(dfSubset$FUNCTION_LOC))
##        }
##
##        yAxisScaleSuffix <<- "/LOC"
##    } else if ( value == "COUNT" ) {
##        computeScaleBy <<- function(df, indepValue, includeBiggerP) {
##            if (includeBiggerP) {
##                return ( sum(df$INDEPV >= indepValue) )
##            } else {
##                return ( sum(df$INDEPV == indepValue) )
##            }
##        }
##        
##        yAxisScaleSuffix <<- "/Function"
##    } else if ( value == "none" ) {
##        computeScaleBy <<- function(df, indepValue, includeBiggerP) {
##            return (1)
##        }
##        
##        yAxisScaleSuffix <<- ""
##    } else {
##        stop(paste("Invalid value for option `--scale': `", value, "'.", sep=""))
##    }
##}
##parseScaleOptValue(opts$scale)

if ( opts$noyaxislabels ) {
    yAxisName <- ""
    yAxisLabels <- sprintf(yLabels, fmt="")
} else {
    if ((opts$dependent == "LCH") || (opts$dependent == "LINES_CHANGED")) {
        yAxisPrefix <- "Lines changed"
    } else if (opts$dependent == "HUNKS") {
        yAxisPrefix <- "Hunks"
    } else if (opts$dependent == "COMMITS") {
        yAxisPrefix <- "Commits"
    } else {
        yAxisPrefix <- opts$dependent
    }
    ##yAxisName <- paste(yAxisPrefix, yAxisScaleSuffix, sep="")
    yAxisName <- yAxisPrefix
    ##yAxisLabels <- sprintf(round(100*yLabels), fmt="%2d%%")
}

corrString <- function(data, indep, dep) {
    x <- eval(parse(text=paste("data", indep, sep="$")))
    y <- eval(parse(text=paste("data", dep, sep="$")))
    r <- suppressWarnings(cor.test(x, y, method="spearman"))
    rho <- r$estimate
    p <- r$p.value
    s <- sprintf("S-rho = %.2f (p=%.4f)\n", rho, p)
    return (s)
}

##computeIndepValue <- function(df, valIndep, includeBiggerP=FALSE) {
##    scaleIndep		<- computeScaleBy(df, valIndep, includeBiggerP)
##    if (includeBiggerP) {
##        ##sumDepvIndep	<- sum(df$DEPV & df$INDEPV >= valIndep)
##        dfSubset	<- subset(df, INDEPV >= valIndep)
##    } else {
##        ##sumDepvIndep	<- sum(df$DEPV & df$INDEPV == valIndep)
##        dfSubset	<- subset(df, INDEPV == valIndep)
##    }
##    ##cat(paste(nrow(dfSubset), sep="\n"))
##    sumDepvIndep	<- sum( dfSubset$DEPV )
##
##    ##cat(paste("DEBUG: ", df$SNAPSHOT_DATE[1], ": ", opts$dependent, "=...", "\n", sep=""))
##    ##cat(paste((df$DEPV & df$INDEPV == valIndep), "\n", sep=""))
##
#####    cat(paste("DEBUG: ", df$SNAPSHOT_DATE[1], ": sum(", opts$dependent, ") where ",
#####              opts$independent, (if (includeBiggerP) '>=' else '=' ), valIndep,
#####              " = ", sumDepvIndep, "\n", sep=""))
##
##    if (is.na(scaleIndep) || (scaleIndep == 0)) {
##        snapshotDate <- df$SNAPSHOT_DATE[1]
##        cmp <- if (includeBiggerP) '>=' else '=='
##        cat(paste("WARNING: ", "Invalid scale factor for indep", cmp, valIndep,
##                  " values in snapshot ",
##                  snapshotDate, ": ", scaleIndep, "\n", sep=""))
##        return (NaN)
##    } else {
##        return (sumDepvIndep * 1.0 / scaleIndep)
##    }
##}
##
##processSnapshot <- function(df) {
##    snapshotNo <- df$SNAPSHOT[1]
##    snapshotDate <- df$SNAPSHOT_DATE[1]
##    
##    ## Replace all missing values with zeroes
##    df[is.na(df)] <- 0.0
##
##    ixIndep <- which( colnames(df)==opts$independent )
##    ixDep   <- which( colnames(df)==opts$dependent )
##
##    df$INDEPV	<- df[,ixIndep]
##    df$DEPV	<- df[,ixDep]
##
##    r <- data.frame(CommitWindow=c(snapshotNo
##                                 , snapshotNo
##                                 , snapshotNo
##                                        #, snapshotNo
##                                        #, snapshotNo
##                                   ),
##                    Value=c(computeIndepValue(df, 0)
##                          , computeIndepValue(df, 1)
##                          , computeIndepValue(df, 2, TRUE)
##                          ##, computeIndepValue(df, 3),
##                          ##, computeIndepValue(df, 3, TRUE)
##                            ),
##                    Marker=c('=0'
##                           , '=1'
##                           , '>1'
##                             ##, '3'
##                             ##, '>3'
##                             ))
##    
##    return (r)
##}

##warnings()

appendUnlessEmpty <- function(a, b, sep="") {
    if (a == "") {
        return (b)
    } else {
        return (paste(a,b,sep=sep))
    }
}

allData <- readData(args)
allData$CND <- allData$NONEST

filteredData <- allData
filteredData[is.na(filteredData)] <- 0.0

formatf <- function(f) {
    return (sub("\\.$", "", sub("[0]+$", "", sprintf("%f", f))))
}

filterName <- ""
filterDisplayName <- ""

if (opts$annotated=="yes") {
    filteredData <- subset(filteredData, FL >= 0.5)
    filterName <- appendUnlessEmpty(filterName, "annotated", sep=".")
    filterDisplayName <- appendUnlessEmpty(filterDisplayName, "annotated", sep=", ")
} else if (opts$annotated=="no") {
    filteredData <- subset(filteredData, FL < 0.5)
    filterName <- appendUnlessEmpty(filterName, "unannotated", sep=".")
    filterDisplayName <- appendUnlessEmpty(filterDisplayName, "un-annotated", sep=", ")
} else if (opts$annotated != "both") {
    stop(paste("Invalid value for option `-a':", opts$annotated))
}

if (opts$changed) {
    filteredData <- subset(filteredData, COMMITS > 0)
    filterName <- appendUnlessEmpty(filterName, "changed", sep=".")
    filterDisplayName <- appendUnlessEmpty(filterDisplayName, "changed", sep=", ")
}

if (filterName == "") {
    filterName <- "all"
    filterDisplayName <- "all"
}

titleExtra <- paste("(", filterDisplayName, " functions", ")", sep="")

## Compute correlation coefficient before removing outliers.  Outliers
## are only removed for display purposed, not for computing the
## correlations.
corrText <- corrString(filteredData, opts$independent, opts$dependent)

eprintf("WARN: rounding independent variable values!\n")
filteredData[,opts$independent] <- round(filteredData[,opts$independent])

## Remove outliers for display puroposes (Only do this *after* computing Spearman's rho!)
if (opts$ilim > 0.0) {
    filteredData <- applyRelLim(filteredData, opts$independent, opts$ilim)
    xAxisName <- sprintf("%s (infrequency limit: %s%%)", xAxisName,
                         formatf(opts$ilim))
}

if (opts$dlim > 0.0) {
    filteredData <- applyRelLim(filteredData, opts$dependent, opts$dlim)
    yAxisName <- sprintf("%s (infrequency limit: %s%%)", yAxisName,
                         formatf(opts$dlim))
}

filteredData$INDEP <- filteredData[,opts$independent]
filteredData$DEP <- filteredData[,opts$dependent]

##filteredData$INDEP <- round(filteredData$INDEP)

## Default width and height of PDFs is 7 inches
pdf(file=outputFn,width=7,height=7)

p <- ggplot(data=filteredData, aes(x=INDEP, y=DEP)) +
    geom_point(shape=16) +
    geom_smooth(
      #  se=FALSE  # Don't add shaded confidence region
      #, size=0.85 # line thickness
      ##, method="loess" # loess is the default for datasets with n<1000 anyway
      , span = 1.0 # amount of smoothness; defaults to 0.75; bigger = smoother
    )
            
##p <- p +
##    scale_colour_discrete(name=opts$independent) +
##    scale_shape_discrete(name =opts$independent)

##scaleColorList <- lapply(seq(1, length(plotData)), getLegendColorTranslationElt)
##scaleColorDf <- Reduce(rbind, scaleColorList)

##p <- p +
##    scale_color_manual("" # what's that for?
##                       , breaks=scaleColorDf$breaks
##                       , values=scaleColorDf$values)

## Axis labels
p <- p + labs(x = xAxisName, y = yAxisName)
## Plot title
p <- p + ggtitle(sprintf("%s %s | n=%d | %s", systemname, titleExtra, nrow(filteredData),
                         corrText))

## Change range of shown values on y-axis
##if (!is.null(opts$ymax)) {
##    p <- p + ylim(c(0,opts$ymax)) 
##}

print(p)

invisible(dev.off())

cat(outputFn,"\n",sep="")