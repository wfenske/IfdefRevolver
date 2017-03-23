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

    baseDir <-opts$snapshotresultsdir
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
        computeScaleBy <<- function(df, indepValue) {
            return ( sum(df$FUNCTION_LOC & df$INDEP_BOOL == indepValue) )
        }

        yAxisScaleSuffix <<- "/LOC"
    } else if ( value == "COUNT" ) {
        computeScaleBy <<- function(df, indepValue) {
            return ( sum(df$INDEP_BOOL == indepValue) )
        }
        
        yAxisScaleSuffix <<- "/FUNCTION"
    } else if ( value == "none" ) {
        computeScaleBy <<- function(df, indepValue) {
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
                               , "FUNCTION_SIGNATUE"="string"
                               , "FUNCTION_LOC"="numeric"
                               , "HUNKS"="numeric"
                               , "COMMITS"="numeric"
                               , "BUGFIXES"="numeric"
                               , "LINE_DELTA"="numeric"
                               , "LINES_DELETD"="numeric"
                               , "LINES_ADDED"="numeric"
                               , "LOAC"="numeric"
                               , "LOFC"="numeric"
                               , "NOFL"="numeric"
                               , "NOFC_Dup"="numeric"
                               , "NOFC_NonDup"="numeric"
                               , "NONEST"="numeric"))
    cat(snapshotData$SNAPSHOT_DATE[1], "\n")
    snapshotData["SNAPSHOT"] <- snapshotIx
    ## Change the value of the global variable using <<-
    ##cat(str(max(as.numeric(snapshotData$FUNCTION_LOC), na.rm=T)))
    ##cat(str(max(snapshotData$FUNCTION_LOC), na.rm=T))
    snapshotIx <<- snapshotIx + 1
    return (snapshotData)
}

processSnapshot <- function(df) {
    snapshotNo <- df$SNAPSHOT[1]
    snapshotDate <- df$SNAPSHOT_DATE[1]
    
    ixLoc		<- which( colnames(df)=="FUNCTION_LOC")
    ##ixHunks		<- which( colnames(df)=="HUNKS")
    ##ixCommits		<- which( colnames(df)=="COMMITS")
    ##ixBugfixes	<- which( colnames(df)=="BUGFIXES")
    ##ixAbSmell		<- which( colnames(df)=="ABSmell")
    ##ixLoac		<- which( colnames(df)=="LOAC")
    ##ixLofc		<- which( colnames(df)=="LOFC")
    ##ixNolf		<- which( colnames(df)=="NOFL")
    ##ixNofcDup		<- which( colnames(df)=="NOFC_Dup")
    ##ixNofcNonDup	<- which( colnames(df)=="NOFC_NonDup")

    ## Replace all missing values with zeroes
    df[is.na(df)] <- 0.0

    ##ixIndep <- ixAbSmell
    ##ixDep <- ixBugfixes
    ixIndep <- which( colnames(df)==opts$independent )
    ixDep   <- which( colnames(df)==opts$dependent )

    df$INDEPV	<- df[,ixIndep]
    df$DEPV	<- df[,ixDep]

    ## e.g., INDEP=NOFL, DEP=HUNKS
    df$INDEP_BOOL	<- df$INDEPV > 0 ## e.g., NOLF > 0

    ##sumLocIndepFalse	<- sum(df$FUNCTION_LOC & df$INDEP_BOOL == FALSE)
    scaleIndepFalse	<- computeScaleBy(df, FALSE)
    sumDepvIndepFalse	<- sum(df$DEPV & df$INDEP_BOOL == FALSE)

    ##sumLocIndepTrue	<- sum(df$FUNCTION_LOC & df$INDEP_BOOL == TRUE)
    scaleIndepTrue	<- computeScaleBy(df, TRUE)
    sumDepvIndepTrue	<- sum(df$DEPV & df$INDEP_BOOL == TRUE)

    if (is.na(scaleIndepFalse) || (scaleIndepFalse == 0)) {
        resIndepFalse	<- NaN
        cat(paste("WARNING: ", "Invalid scale factor for indep=false values in snapshot ", snapshotDate, ": ", scaleIndepFalse, "\n", sep=""))
    } else {
        resIndepFalse	<- sumDepvIndepFalse / scaleIndepFalse
    }

    if (is.na(scaleIndepTrue) || (scaleIndepTrue == 0)) {
        resIndepTrue	<- NaN
        cat(paste("WARNING: ", "Invalid scale factor for indep=true values in snapshot ", snapshotDate, ": ", scaleIndepTrue, "\n", sep=""))
    } else {
        resIndepTrue	<- sumDepvIndepTrue / scaleIndepTrue
    }

    ##df[,'DEP']   <- df[,ixDep]   > 0

    ##numFuncs <- nrow(df)
    ##numIndepFalse	<- nrow(df[df$INDEP == FALSE,])
    ##numIndepTrue	<- nrow(df[df$INDEP == TRUE,])

    ##numDepTrueIndepFalse	<- nrow(df[df$DEP == TRUE & df$INDEP == FALSE,])
    ##numDepTrueIndepTrue		<- nrow(df[df$DEP == TRUE & df$INDEP == TRUE,])

    ## ratio of functions with dep = true (e.g., function is
    ## fault-prone) given that indep = false (e.g., function is not
    ## smelly)
    ##cleanRatio <- numDepTrueIndepFalse / numIndepFalse
    
    ## ratio of functions with dep = true (e.g., function is
    ## fault-prone) given that indep = true (e.g., function is smelly)
    ##dirtyRatio <- numDepTrueIndepTrue / numIndepTrue

    ##return (c(cleanRatio,dirtyRatio))

    r <- data.frame(CommitWindow=c(snapshotNo,snapshotNo),
                    Value=c(resIndepFalse, resIndepTrue),
                    Marker=c(0,1))
    
    return (r)
}

listOfSnapshotData <- lapply(inputFns, readSnapshotFile)

##warnings()

## returns a list of cleanRatio, dirtyRatio pairs
listOfValueFrames <- lapply(listOfSnapshotData, processSnapshot)
combinedValueFrame <- Reduce(rbind, listOfValueFrames)

## Default width and height of PDFs is 7 inches
pdf(file=outputFn,width=7,height=4)
##p <- ggplot(combinedValueFrame,
##            aes(x=CommitWindow, colour=Marker)) +
##    geom_density() +
##    ggtitle(paste(yAxisName, " in ", systemname, sep=""))
myAes <-aes(x=CommitWindow, y=Value)
dFalse <- subset(combinedValueFrame, Marker==0)
dTrue  <- subset(combinedValueFrame, Marker==1)
smoothMethod <- "loess"
smoothSize <- 1
showSE <- FALSE
colorFalse="blue"
colorTrue="red"
## See http://www.sthda.com/english/wiki/ggplot2-point-shapes for more
## shapes for geom_point
shapeFalse <- 16 # 16=a black, filled dot
shapeTrue  <- 17 # 17=a black, filled triangle

p <- ggplot() +
    ## blue plot
    geom_point(data=dFalse, myAes, shape=shapeFalse) + 
    geom_smooth(data=dFalse
              , myAes, colour=colorFalse, fill=colorFalse, se=showSE
              , size=smoothSize
              , method=smoothMethod
                ) +
    ## red plot
    geom_point(data=dTrue, myAes, shape=shapeTrue) + 
    geom_smooth(data=dTrue
              , myAes,  colour=colorTrue, fill=colorTrue, se=showSE
              , size=smoothSize
              , method=smoothMethod
                ) +

    ## Other graph settings
    labs(x = "Commit Window", y = yAxisName) +
    ggtitle(systemname)

## Change range of shown values on y-axis
if (!is.null(opts$ymax)) {
    p <- p + ylim(c(0,opts$ymax)) 
}

print(p)

invisible(dev.off())

cat(outputFn,"\n",sep="")
