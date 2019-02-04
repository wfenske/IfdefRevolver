#!/usr/bin/env Rscript

## Example:
invisible('
for indep in NOFL ABSmell NOFC_Dup NONEST; do
   for dep in HUNKS COMMITS BUGFIXES; do
     for scale in LOC COUNT none; do
       for p in apache busybox openldap openvpn pidgin sqlite; do
         echo "Processing $p"
         ratioscmp.R --ymax=0.5 --independent=$indep --dependent=$dep --scale=$scale -s results/$p
       done
     done
   done
done'
)

library(optparse)
library(ggplot2)

options <- list(
    make_option(c("-o", "--output")
              , help="Name of output file."
              , default = NULL
                )
  , make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `<projec-name>/results/allDataAge.rdata' below the current working directory."
              , default = NULL
                )
  , make_option(c("-i", "--independent")
              , help="Name of independent variable.  Valid values are: ABSmell, LocationSmell, ConstantsSmell, NestingSmell, FL (a.k.a. NOFL), FC (a.k.a. NOFC_NonDup), ND (a.k.a. NONEST). [default: %default]"
                ## NOTE, 2017-03-18, wf: We also have LOAC, LOFC
              , default="FL"
                )
  , make_option(c("-d", "--dependent")
                , help="Name of independent variable.  Valid values are, e.g.: HUNKS, COMMITS, LINES_CHANGED. [default: %default]"
                , default="COMMITS"
                )
  , make_option(c("-s", "--scale")
              , help="Factor by which the dependent variable should be scaled.  For instance, if --dependent=COMMITS and --scale=LOC, then the dependent variable will be COMMITS/LOC, i.e., the frequency of changes per lines of code.  Valid values are: LOC (lines per code in the function), COUNT (number of functions), none (take the value of the dependent variable as is). [default: %default]"
                , default="LOC"
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
  , make_option("--ymax"
              , default=NULL
              , type="double"
              , help="Upper limit for values on the Y-axis.  Limits be determined automatically if not supplied."
                )
  , make_option(c("--debug")
              , help="Print some diagnostic messages. [default: %default]"
              , default = FALSE
              , action="store_true"
                )
)

args <- parse_args(OptionParser(
    description = "Plot ratio of some dependent variable (e.g., number of commits) for functions having some property (e.g., FL > 0) vs. those that don't have the property. Inputs are CSV files where each CSV file represent the data for one snapshot. If no input files are named, the directory containing the results for all the snapshots must be specified via the `--snapshotsdir' (`-s') option."
  , usage = "%prog [options]... [file]..."
  , option_list=options)
  , positional_arguments = c(0, Inf))

opts <- args$options

readData <- function(commandLineArgs) {
    opts <- commandLineArgs$options
    if ( is.null(opts$project) ) {
        stop("Specify the name of the project the `--project' option (`-p' for short).")
    }
    dataFn <-  file.path(opts$project, "results", "allDataAge.rdata")
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
    outputFn <- paste("ratios-", opts$independent, "-", opts$dependent, ".", opts$scale, "-", systemname, ".pdf", sep="")
}

xAxisName <- "Commit Window"
yLabels <- seq(0, 1.0, 0.25)
axisNameFontScale <- 1.5
plotNameFontScale <- 1.75

parseScaleOptValue <- function(value) {
    if ( value == "LOC" ) {
        computeScaleBy <<- function(df, indepValue, includeBiggerP) {
            if (includeBiggerP) {
                dfSubset <- subset(df, INDEPV >= indepValue)
            } else {
                dfSubset <- subset(df, INDEPV == indepValue)
            }
            return (sum(dfSubset$LOC))
        }

        yAxisScaleSuffix <<- "/LOC"
    } else if ( value == "COUNT" ) {
        computeScaleBy <<- function(df, indepValue, includeBiggerP) {
            if (includeBiggerP) {
                return ( sum(df$INDEPV >= indepValue) )
            } else {
                return ( sum(df$INDEPV == indepValue) )
            }
        }
        
        yAxisScaleSuffix <<- "/Function"
    } else if ( value == "none" ) {
        computeScaleBy <<- function(df, indepValue, includeBiggerP) {
            return (1)
        }
        
        yAxisScaleSuffix <<- ""
    } else {
        stop(paste("Invalid value for option `--scale': `", value, "'.", sep=""))
    }
}

parseScaleOptValue(opts$scale)

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
    yAxisName <- paste(yAxisPrefix, yAxisScaleSuffix, sep="")
    yAxisLabels <- sprintf(round(100*yLabels), fmt="%2d%%")
}

computeIndepValue <- function(df, valIndep, includeBiggerP=FALSE) {
    scaleIndep		<- computeScaleBy(df, valIndep, includeBiggerP)
    if (includeBiggerP) {
        ##sumDepvIndep	<- sum(df$DEPV & df$INDEPV >= valIndep)
        dfSubset	<- subset(df, INDEPV >= valIndep)
    } else {
        ##sumDepvIndep	<- sum(df$DEPV & df$INDEPV == valIndep)
        dfSubset	<- subset(df, INDEPV == valIndep)
    }
    ##cat(paste(nrow(dfSubset), sep="\n"))
    sumDepvIndep	<- sum( dfSubset$DEPV )

    ##cat(paste("DEBUG: ", df$SNAPSHOT_DATE[1], ": ", opts$dependent, "=...", "\n", sep=""))
    ##cat(paste((df$DEPV & df$INDEPV == valIndep), "\n", sep=""))

###    cat(paste("DEBUG: ", df$SNAPSHOT_DATE[1], ": sum(", opts$dependent, ") where ",
###              opts$independent, (if (includeBiggerP) '>=' else '=' ), valIndep,
###              " = ", sumDepvIndep, "\n", sep=""))

    if (is.na(scaleIndep) || (scaleIndep == 0)) {
        snapshotDate <- df$SNAPSHOT_DATE[1]
        cmp <- if (includeBiggerP) '>=' else '=='
        cat(paste("WARNING: ", "Invalid scale factor for indep", cmp, valIndep,
                  " values in snapshot ",
                  snapshotDate, ": ", scaleIndep, "\n", sep=""))
        return (NaN)
    } else {
        return (sumDepvIndep * 1.0 / scaleIndep)
    }
}

processSnapshot <- function(df) {
    snapshotNo <- df$SNAPSHOT_INDEX[1]
    snapshotDate <- df$SNAPSHOT_DATE[1]
    
    ## Replace all missing values with zeroes
    df[is.na(df)] <- 0.0

    ixIndep <- which( colnames(df)==opts$independent )
    ixDep   <- which( colnames(df)==opts$dependent )

    df$INDEPV	<- df[,ixIndep]
    df$DEPV	<- df[,ixDep]

    r <- data.frame(CommitWindow=c(snapshotNo
                                 , snapshotNo
                                 , snapshotNo
                                        #, snapshotNo
                                        #, snapshotNo
                                   ),
                    Value=c(computeIndepValue(df, 0)
                          , computeIndepValue(df, 1)
                          , computeIndepValue(df, 2, TRUE)
                          ##, computeIndepValue(df, 3),
                          ##, computeIndepValue(df, 3, TRUE)
                            ),
                    Marker=c('=0'
                           , '=1'
                           , '>1'
                             ##, '3'
                             ##, '>3'
                             ))
    
    return (r)
}

##listOfSnapshotData <- lapply(inputFns, readSnapshotFile)

##warnings()

allData <- readData(args)
##AGE_GROUP <- cut(allData$AGE, 3, include.lowest=TRUE,
##                 ##labels=c("Low", "Med", "High")
##                 labels=c(0, 1, 2)
##                 )
##FL_GROUP <- cut(allData$FL, 3, include.lowest=TRUE,
##                 ##labels=c("Low", "Med", "High")
##                 labels=c(0, 1, 2)
##                 )
##cutData <- cbind(allData, AGE_GROUP, FL_GROUP)
cutData <- allData

## returns a list of cleanRatio, dirtyRatio pairs
listOfValueFrames <- lapply(split(cutData, factor(cutData$SNAPSHOT_INDEX)),
                            processSnapshot)
combinedValueFrame <- Reduce(rbind, listOfValueFrames)

## Default width and height of PDFs is 7 inches
pdf(file=outputFn,width=7,height=2.25)

## See http://www.sthda.com/english/wiki/ggplot2-point-shapes for more
## shapes for geom_point
shapes <- list(16 # 16=a black, filled dot
             , 17 # 17=a black, filled triangle
             , 18
             , 19
             , 20
               )

p <- ggplot(data=combinedValueFrame, aes(x=CommitWindow, y=Value,
                                         group=Marker, shape=Marker,
                                         colour=Marker)) +
    geom_point() +
    geom_smooth(
        se=FALSE
      , size=0.85 # line thickness
      , method="loess" # loess is the default for datasets with n<1000 anyway
      , span = 0.15 # amount of smoothness; defaults to 0.75; bigger = smoother
    )

p <- p +
    scale_colour_discrete(name=opts$independent) +
    scale_shape_discrete(name =opts$independent)

##scaleColorList <- lapply(seq(1, length(plotData)), getLegendColorTranslationElt)
##scaleColorDf <- Reduce(rbind, scaleColorList)

##p <- p +
##    scale_color_manual("" # what's that for?
##                       , breaks=scaleColorDf$breaks
##                       , values=scaleColorDf$values)

## Other graph settings
p <- p +
     labs(x = "Commit Window", y = yAxisName)
     ##+ ggtitle(systemname)

## Change range of shown values on y-axis
if (!is.null(opts$ymax)) {
    p <- p + ylim(c(0,opts$ymax)) 
}

print(p)

invisible(dev.off())

cat(outputFn,"\n",sep="")
