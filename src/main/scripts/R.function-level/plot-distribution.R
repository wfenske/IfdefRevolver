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
    
    , make_option(c("-a", "--annotation_variable")
              , help="Name of the annotation variable (FL, FC, or ND) whose distribution should be plotted."
              , default = NULL
                )
    
    , make_option(c("--amin")
                , help="Lower limit to the frequency of occurrence of the values specified via `-a' to be included in the histogram.  Given as a percentage. [default: %default]"
                , default = 1.0
                )
    
    , make_option(c("-c", "--change_variable")
              , help="Name of the change variable (COMMITS, HUNKS, LINES_CHANGED) whose distribution should be plotted."
              , default = NULL
                )
    
    , make_option(c("--cmin")
                , help="Lower limit to the frequency of occurrence of the values specified via `-c' to be included in the histogram.  Given as a percentage. [default: %default]"
                , default = 1.0
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

ggplotHist <- function(df, varName) {
    uniqueValues <- unique(df[,varName])

    return (ggplot(df, aes(x=eval(varSymbolExpr(varName))))
            + geom_histogram(color="black"
                             ##, fill="lightblue"
                             ## , linetype="dashed"
                           , bins=length(uniqueValues)
                             )
            + scale_x_discrete(limits=sort(uniqueValues),name=varName)
            ##+ scale_y_log10()
            + ggtitle(opts$name)
            )
}

outputFn <- function(varName) {
    return (paste(paste("distribution", varName, opts$name, sep="_"),
                  ".pdf", sep=""))
}

openOutputFile <- function(varName) {
    fn <- outputFn(varName)
    pdf(file=fn,width=6,height=4)
    return (fn)
}

println <- function (line) {
    cat(line,"\n",sep="")
}

createHistPlot <- function(data, varName) {
    fn <- openOutputFile(varName)
    p <- ggplotHist(data, varName)
    print(p)
    invisible(dev.off())
    println(fn)
    return (fn)
}

createHistPlotComfy <- function(baseData, varName, minLim) {
    values <- baseData[, varName]
    minLimD <- as.double(minLim)

    threshold <- quantile(values, c((100.0 - minLimD) / 100.0))
    dataFiltered <- subset(baseData,
                           eval(varSymbolExpr(varName)) <= threshold[1])
    
    r <- createHistPlot(dataFiltered, varName)

    return (r)
}

allData <- readData(args)

havePlot <- FALSE

annVar <- opts$annotation_variable
chgVar <- opts$change_variable

if (!is.null(annVar)) {
    annData <- subset(allData, FL > 0)
    dummy <- createHistPlotComfy(annData, annVar, opts$amin)
    havePlot <- TRUE
}

if (!is.null(chgVar)) {
    chgData <- subset(allData, COMMITS > 0)
    dummy <- createHistPlotComfy(chgData, chgVar, opts$cmin)
    havePlot <- TRUE
}

if (!havePlot) {
    stop("Missing variable name.  Specify via `--annotation_variable' (`-a') or `--change_variable' (`-c').")
}
