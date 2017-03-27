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
  , make_option(c("-s", "--snapshotresultsdir")
              , help="Name of the directory where the snaphsot results are located.  This folder is expected to contain folder named, e.g., `1996-07-01', which, in turn, contain CSV files named `joint_function_ab_smell_snapshot.csv'."
              , default = NULL
                )
  , make_option("--independent"
              , help="Name of independent variable.  Valid values are: ABSmell, LocationSmell, ConstantsSmell, NestingSmell, NOFL, NOFC_Dup, NOFC_NonDup, NONEST. [default: %default]"
                ## NOTE, 2017-03-18, wf: We also have LOAC, LOFC
              , default="ABSmell"
                )
  , make_option("--dependent"
                , help="Name of independent variable.  Valid values are: HUNKS, COMMITS, BUGFIXES, LINE_DELTA, LINES_DELETED, LINES_ADDED. [default: %default]"
                , default="BUGFIXES"
                )
  , make_option("--scale"
                , help="Factor by which the dependent variable should be scaled.  For instance, if --dependent=BUGFIXES and --scale=LOC, then the dependent variable will be BUGFIXES/LOC, i.e., the frequency of bug-fixes per lines of code.  Valid values are: LOC (lines per code in the function), COUNT (number of functions), none (take the value of the dependent variable as is). [default: %default]"
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
)

args <- parse_args(OptionParser(
    description = "Plot ratio of some dependent variable (e.g., bug-fixes) for functions having some property (e.g., ABSmell > 0) vs. those that don't have the property. Inputs are CSV files where each CSV file represent the data for one snapshot."
  , usage = "%prog [options]... [file]..."
  , option_list=options)
  , positional_arguments = c(0, Inf))

opts <- args$options

getInputFilenames <- function(commandLineArgs) {
    result <- args$args
    if ( length(result) > 0 ) {
        return (result)
    }

    opts <- args$options
    if ( is.null(opts$snapshotresultsdir) ) {
            stop("Missing input files.  Either specify explicit input files or specify the directory containing the snapshot results via the `--snapshotresultsdir' option (`-s' for short).")
    }

    baseDir <- opts$snapshotresultsdir
    snapshotDirs <- list.files(baseDir, pattern="[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]")
    if (length(snapshotDirs) == 0) {
        stop(paste("No snapshot directories found in `", baseDir, "'", sep=""))
    }
    return (lapply(snapshotDirs, function(snapshotDir) { file.path(baseDir, snapshotDir, "joint_function_ab_smell_snapshot.csv") }))
}

inputFns <- getInputFilenames(args)

if ( is.null(opts$systemname) ) {
    systemdir <- dirname(dirname(inputFns[[1]]))
    systemname <- basename(systemdir)
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
            return (sum(dfSubset$FUNCTION_LOC))
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
        
        yAxisScaleSuffix <<- "/FUNCTION"
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
    yAxisName <- paste(opts$dependent, yAxisScaleSuffix, sep="")
    yAxisLabels <- sprintf(round(100*yLabels), fmt="%2d%%")
}

snapshotIx <- 1

readSnapshotFile <- function(inputFn) {
    snapshotData <- read.csv(inputFn, header=TRUE, sep=",",
                             colClasses=c(
                                 "SNAPSHOT_DATE"="character"
                               , "FUNCTION_SIGNATURE"="character"
                               , "FUNCTION_LOC"="numeric"
                               , "HUNKS"="numeric"
                               , "COMMITS"="numeric"
                               , "BUGFIXES"="numeric"
                               , "LINE_DELTA"="numeric"
                               , "LINES_DELETED"="numeric"
                               #, "LINES_ADDED"="numeric"
                               #, "LOAC"="numeric"
                               , "LOFC"="numeric"
                               , "NOFL"="numeric"
                               , "NOFC_Dup"="numeric"
                               , "NOFC_NonDup"="numeric"
                               , "NONEST"="numeric"))
    cat("INFO: Reading snapshot ", snapshotData$SNAPSHOT_DATE[1], "\n", sep="")
    snapshotData["SNAPSHOT"] <- snapshotIx
    ## Change the value of the global variable using <<-
    ##cat(str(max(as.numeric(snapshotData$FUNCTION_LOC), na.rm=T)))
    ##cat(str(max(snapshotData$FUNCTION_LOC), na.rm=T))
    snapshotIx <<- snapshotIx + 1
    return (snapshotData)
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
    snapshotNo <- df$SNAPSHOT[1]
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

listOfSnapshotData <- lapply(inputFns, readSnapshotFile)

##warnings()

## returns a list of cleanRatio, dirtyRatio pairs
listOfValueFrames <- lapply(listOfSnapshotData, processSnapshot)
combinedValueFrame <- Reduce(rbind, listOfValueFrames)

## Default width and height of PDFs is 7 inches
pdf(file=outputFn,width=7,height=4)

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
      , span = 0.5 # amount of smoothness; defaults to 0.75; bigger = smoother
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
    labs(x = "Commit Window", y = yAxisName) +
    ggtitle(systemname)

## Change range of shown values on y-axis
if (!is.null(opts$ymax)) {
    p <- p + ylim(c(0,opts$ymax)) 
}

print(p)

invisible(dev.off())

cat(outputFn,"\n",sep="")
