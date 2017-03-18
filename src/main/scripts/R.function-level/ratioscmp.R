#!/usr/bin/env Rscript

library(optparse)

options <- list(
    make_option(c("-o", "--output")
              , help="Name of output file."
              , default = NULL
                )
  , make_option(c("-Y", "--noyaxislabels")
              , action="store_true"
              , default=FALSE
              , help="Omit name and labels of y axis [default %default]"
                )
  , make_option(c("-d", "--delimiter")
              , default=","
              , help="Column delimiter, e.g. `,' or `;'. [default: %default]"
                )
  , make_option("--independent"
              , help="Name of independent variable.  Valid values are: ABSmell,LocationSmell,ConstantsSmell,NestingSmell,NOFL,NOFC_Dup,NOFC_NonDup,NONEST. [default: %default]"
                ## NOTE, 2017-03-18, wf: We also have LOAC,LOFC
              , default="ABSmell"
                )
  , make_option("--dependent"
                , help="Name of independent variable.  Valid values are: HUNKS,COMMITS,BUGFIXES,LINE_DELTA,LINES_DELETED,LINES_ADDED. [default: %default]"
                , default="BUGFIXES"
                )
  , make_option(c("-n", "--systemname")
              , default=NULL
              , help="Name of the system; will be guessed from the input filenames if omitted"
                )
)

args <- parse_args(OptionParser(
    description = "Plot ratio of some dependent variable (e.g., bug-fixes) for functions having some property (e.g., ABSmell > 0) vs. those that don't have the property. Inputs are CSV files where each CSV file represent the data for one snapshot."
  , usage = "%prog [options]... [file]..."
  , option_list=options)
  , positional_arguments = c(1, Inf))
opts <- args$options
inputFns <- args$args

if ( !is.null(opts$systemname) ) {
    systemname <- opts$systemname
} else {
    systemdir <- dirname(inputFns[0])
    systemname <- basename(systemdir)
}

if ( !is.null(opts$output) ) {
    outputFn <- opts$output
} else {
    outputFn <- paste(systemname, "pdf", sep=".")
}

xAxisName <- "Commit Window"
yLabels <- seq(0, 1.0, 0.25)
axisNameFontScale <- 1.5
plotNameFontScale <- 1.75

if ( opts$noyaxislabels ) {
    yAxisName <- ""
    yAxisLabels <- sprintf(yLabels, fmt="")
} else {
    yAxisName <- "Percentage"
    yAxisLabels <- sprintf(round(100*yLabels), fmt="%2d%%")
}

snapshotIx <- 1

readSnapshotFile <- function(inputFn) {
    snapshotData <- read.csv(inputFn, head=TRUE, sep=opts$delimiter)
    snapshotData["SNAPSHOT"] <- snapshotIx
    ## Change the value of the global variable using <<-
    snapshotIx <<- snapshotIx + 1
    return (snapshotData)
}

processSnapshot <- function(snapshotData) {
    ixLoc		<- which( colnames(snapshotData)=="FUNCTION_LOC")
    ixHunks		<- which( colnames(snapshotData)=="HUNKS")
    ixCommits		<- which( colnames(snapshotData)=="COMMITS")
    ixBugfixes		<- which( colnames(snapshotData)=="BUGFIXES")
    ixAbSmell		<- which( colnames(snapshotData)=="ABSmell")
    ixLoac		<- which( colnames(snapshotData)=="LOAC")
    ixLofc		<- which( colnames(snapshotData)=="LOFC")
    ixNolf		<- which( colnames(snapshotData)=="NOFL")
    ixNofcDup		<- which( colnames(snapshotData)=="NOFC_Dup")
    ixNofcNonDup	<- which( colnames(snapshotData)=="NOFC_NonDup")

    averageFixesPerLoc	<- sum(snapshotData[ixBugfixes]) / sum(snapshotData[ixLoc])

    ## Replace all missing values with zeroes
    snapshotData[is.na(snapshotData)] <- 0.0

    ixIndep <- ixAbSmell
    ixDep <- ixBugfixes

    snapshotData[,'INDEP'] <- snapshotData[,ixIndep] > 0.0
    snapshotData[,'DEP'] <- snapshotData[,ixDep] > 0

    ##numFuncs <- nrow(snapshotData)
    numIndepFalse	<- nrow(snapshotData[snapshotData$INDEP == FALSE,])
    numIndepTrue	<- nrow(snapshotData[snapshotData$INDEP == TRUE,])

    numDepTrueIndepFalse	<- nrow(snapshotData[snapshotData$DEP == TRUE & snapshotData$INDEP == FALSE,])
    numDepTrueIndepTrue		<- nrow(snapshotData[snapshotData$DEP == TRUE & snapshotData$INDEP == TRUE,])

    ## ratio of functions with dep = true (e.g., function is
    ## fault-prone) given that indep = false (e.g., function is not
    ## smelly)
    cleanRatio <- numDepTrueIndepFalse / numIndepFalse
    
    ## ratio of functions with dep = true (e.g., function is
    ## fault-prone) given that indep = true (e.g., function is smelly)
    dirtyRatio <- numDepTrueIndepTrue / numIndepTrue

    return (c(cleanRatio,dirtyRatio))
}

listOfSnapshotData <- lapply(inputFns, readSnapshotFile)

## returns a list of cleanRatio, dirtyRatio pairs
listOfRatios <- lapply(listOfSnapshotData, processSnapshot)

##listOfRatios

df <- data.frame(Reduce(rbind, listOfRatios))
df

# Default width and height of PDFs is 7 inches
pdf(file=outputFn,width=7,height=4)

par(mfrow=c(1,1))
plot(df[,1], type="l", col="blue", ylim=c(0,1),
     main=systemname,
     xlab=xAxisName,
     ylab=yAxisName, yaxt='n',
     cex.lab=axisNameFontScale,
     cex.main=plotNameFontScale)
axis(2, at=yLabels, labels=yAxisLabels, las=1)
par(new=TRUE)
plot(df[,2], type="l", col="red", ylim=c(0,1),
     xlab="", xaxt='n',
     ylab="", yaxt='n')

invisible(dev.off())
