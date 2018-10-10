#!/usr/bin/env Rscript

library(optparse)
library(ggplot2)

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `results/<projec-name>/allData.rdata' below the current working directory."
              , default = NULL
                )
    
    , make_option(c("-n", "--name")
              , help="Pretty name of project, to be added to the plot."
              , default = NULL
                )
    
    , make_option(c("-v", "--variable")
              , help="Name of the variable whose distribution to plot. Valid names include FC, FL, or ND for annotations, COMMMITS, HUNKS, LINES_CHANGED for changes, FCratio, FLratio, ... COMMITSratio, HUNKSratio, and LCHratio for those variables scaled to the function's LOC."
              , default = NULL
                )
    
    , make_option(c("--min")
                , help="Lower limit to the frequency of occurrence of the values specified via `-v' to be included in the histogram.  Given as a percentage.  This option basically controls the lenght of the tail in case the distribution of the variable has a long tail.  [default: %default]"
                , type = "numeric",
                , default = 1.0
                )
    
    , make_option(c("--maxx")
                , help="Absolute maximum value of values on the x-axis to display."
                , type = "numeric",
                , default = NULL
                )
    
    , make_option(c("--maxy")
                , help="Absolute maximum value of values on the y-axis to display."
                , type = "numeric",
                , default = NULL
                )
    
    , make_option(c("-c", "--changed")
                , help="Consider only the values of changed functions. [default: %default]"
                , action = "store_true"
                , default = FALSE
                )
    
    , make_option(c("--logy")
                , help="Use a log-10 scale for the Y-axis values. [default: %default]"
                , action = "store_true"
                , default = FALSE
                )
    
    , make_option(c("-a", "--annotated")
                , help="Whether to consider only annotated function or only un-annotated functions or both. [default: %default]"
                , default = "both"
                , metavar = "{yes,no,both}"
                )
)

args <- parse_args(OptionParser(
    description = "Plot the distribution of various variables (e.g., number of feature locations (FL), number of commits (COMMITS)) in a project. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

if (is.null(opts$name)) {
    stop("Missing pretty name.  Specify via `--name' (`-n').")
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
        dataFn <-  file.path("results", opts$project, "allData.rdata")
    }
    write(paste("DEBUG: Reading data from ", dataFn, sep=""), stderr())
    result <- readRDS(dataFn)
    write("DEBUG: Sucessfully read data.", stderr())
    return (result)
}

varSymbolExpr <- function(varName) {
    return (parse(text=(varName)))
}

plotDensity <- function(values) {
    d <- density(values) # returns the density data 
    p <- plot(d
       , main=paste("Density of", opts$variable, "in", opts$name)
       , xlab=opts$variable
       , log="y"
         )
    return (p)
}

plotHist <- function(values) {
    low <- quantile(values, c(1 / 100.0))[1]
    high <- quantile(values, c(99 / 100.0))[1]

    x <- values[ values > low & values <= high ]
    
    h<-hist(x
          , breaks=min(10,length(unique(x)))
          , col="red"
          , xlab=opts$variable
          , main=paste("Histogram of", opts$variable, "in", opts$name)
            )
    ## Add a normal curve
    
    ##xfit<-seq(min(x),max(x),length=40) 
    ##yfit<-dnorm(xfit,mean=mean(x),sd=sd(x)) 
    ##yfit <- yfit*diff(h$mids[1:2])*length(x) 
    ##lines(xfit, yfit, col="blue", lwd=2)
    return (h)
}

mkTitle <- function(titleExtra="") {
    title <- opts$name
    if (titleExtra != "") {
        title <- paste(title, titleExtra)
    }
    return (title)
}

ggplotHistDiscrete <- function(df, varName, titleExtra="") {
    uniqueValues <- unique(df[,varName])
    maxBins <- 20
    if (length(uniqueValues) <= maxBins) {
        limits <- sort(uniqueValues)
    } else {
        limits <- sapply(seq(from=min(uniqueValues),
                             to=max(uniqueValues),
                             length.out=maxBins),
                         round)
    }

    p <- (ggplot(df, aes(x=eval(varSymbolExpr(varName))))
        + geom_histogram(color="black", bins=length(limits))
        + scale_x_discrete(limits=limits,name=varName)
        + ggtitle(mkTitle(titleExtra)))

    if (opts$logy) {
        p <- p + scale_y_log10()
        p <- p + ylab("log10(#Functions)")
    } else {
        p <- p + ylab("#Functions")
    }
    
    return (p)
}

ggplotHistContinuous <- function(df, varName, titleExtra="") {
    return (ggplot(df, aes(x=eval(varSymbolExpr(varName))))
            + geom_histogram(color="black"
                             ##, fill="lightblue"
                             ## , linetype="dashed"
                             )
            + scale_x_continuous(name=varName)
            ##+ scale_y_log10()
            + ggtitle(mkTitle(titleExtra))
            )
}

outputFn <- function(varName, fileNameExtra="") {
    base <- varName
    if (fileNameExtra != "") {
        base <- paste(varName, fileNameExtra, sep="_")
    }
    return (paste(paste("distribution", base, opts$name, sep="_"),
                  ".pdf", sep=""))
}

openOutputFile <- function(varName, fileNameExtra="") {
    fn <- outputFn(varName, fileNameExtra)
    pdf(file=fn,width=6,height=4)
    return (fn)
}

println <- function (line) {
    cat(line,"\n",sep="")
}

createHistPlot <- function(data, varName, plotFunc,
                           fileNameExtra="", titleExtra="") {
    fn <- openOutputFile(varName, fileNameExtra)
    p <- plotFunc(data, varName, titleExtra)
    print(p)
    invisible(dev.off())
    println(fn)
    return (fn)
}

applyRelLim <- function(baseData, varName, minLim) {
    values <- baseData[, varName]
    threshold <- quantile(values, c((100.0 - minLim) / 100.0))[1]
    return (subset(baseData, eval(varSymbolExpr(varName)) <= threshold))
}

applyAbsLim <- function(baseData, varName, threshold) {
    return (subset(baseData, eval(varSymbolExpr(varName)) <= threshold))
}

appendUnlessEmpty <- function(a, b, sep="") {
    if (a == "") {
        return (b)
    } else {
        return (paste(a,b,sep=sep))
    }
}

allData <- readData(args)

library(polynom)
## Return a sample of the supplied data frame
sampleDf <- function(df, sz) {
    rowCount <- nrow(df)
    if (rowCount < sz) {
        return (df)
    } else {
        return (df[sample(rowCount, sz), ])
    }
}

##polySample <- subset(subset(allData, FL > 0), COMMITS > 0)
##x <- polySample$LOC
##m <- lm(polySample$COMMITS ~ x + I(x^2) + I(x^3))
##c <- m$coefficients
##c
##e0 <- c[[1]]
##e1 <- c[[2]]
##e2 <- c[[3]]
##e3 <- c[[4]] ## e3 is too small and varies too much to be useful
##e0
##e1
##e2
##e3

normLoc <- function(x) {
    return (log2(x))
##    return (1.301374
##            + 0.0208396 * x
##            + -1.725208e-05 * x^2
    ##            + 5.489012e-09 * x^3)
}

allData$NORM_LOC <- normLoc(allData$LOC)
allData$COMMITSratioStable <- allData$COMMITS / allData$NORM_LOC

varName <- opts$variable

if (is.null(varName)) {
    stop("Missing variable name.  Specify via `--variable' (`-v').")
}

if (!(varName %in% colnames(allData))) {
    stop(paste("No such column: `", varName, "'. "
             , "Valid names are: ", paste(colnames(allData), collapse=', ')
             , sep=""))
}

filteredData <- allData

filterName <- ""
filterDisplayName <- ""

if (opts$annotated=="yes") {
    filteredData <- subset(filteredData, FL > 0)
    filterName <- appendUnlessEmpty(filterName, "annotated", sep=".")
    filterDisplayName <- appendUnlessEmpty(filterDisplayName, "annotated", sep=", ")
} else if (opts$annotated=="no") {
    filteredData <- subset(filteredData, FL == 0)
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

if (!is.null(opts$maxx)) {
    filteredData <- applyAbsLim(filteredData, varName, opts$maxx)
}

if (opts$min > 0.0) {
    filteredData <- applyRelLim(filteredData, varName, opts$min)
}

plotFunc <- NULL
if (varName %in% c('FC', 'FL', 'ND', 'COMMITS', 'HUNKS',
                   'LINES_CHANGED', 'LINES_ADDED', 'LINES_DELETED',
                   'LOC', 'LOAC', 'LOFC', 'NUM_WINDOWS')) {
    plotFunc <- ggplotHistDiscrete
} else if (varName %in% c('FCratio', 'FLratio', 'NDratio',
                          'LOACratio', 'LOFCratio',
                          'COMMITSratio', 'HUNKSratio', 'LCHratio',
                          'COMMITSratioStable')) {
        plotFunc <- ggplotHistContinuous
} else {
    stop(paste("Don't know whether to plot variable discreetely or continuously:",
               varName))
}

dummy <- createHistPlot(filteredData, varName, plotFunc, fileNameExtra=filterName,
                        titleExtra=titleExtra)
