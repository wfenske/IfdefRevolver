#!/usr/bin/env Rscript

library(optparse)
library(ggplot2)

eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

options <- list(
    make_option(c("-o", "--output")
              , help="Name of output file."
              , default = NULL
                )
  , make_option(c("-n", "--systemname")
              , default=NULL
              , help="Name of the system"
                )
##                )
    , make_option(c("-c", "--changedOnly")
                , help="Consider only the values of changed functions. [default: %default]"
                , action = "store_true"
                , default = FALSE
                )
    
    , make_option(c("-a", "--annotatedOnly")
                , help="Whether to consider only annotated function or both un-annotated and annotated functions. [default: %default]"
                , action = "store_true"
                , default = FALSE
                )
  , make_option(c("--debug")
              , help="Print some diagnostic messages. [default: %default]"
              , default = FALSE
              , action="store_true"
                )
)

args <- parse_args(OptionParser(
    description = "Plot a heatmap of the correlations in the given CSV file."
  , usage = "%prog [options]... [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))

opts <- args$options

readData <- function(commandLineArgs) {
    fns <- commandLineArgs$args
    if ( length(fns) != 1 ) {
        stop("Expected exactly one input file.")
    }
    fn <- fns[[1]]
    if (opts$debug) {
        eprintf(dataFn, fmt="DEBUG: Reading data from %s", fn)
    }
    result <- read.csv(fn, header=TRUE, sep=",")
    return (result)
}

appendUnlessEmpty <- function(a, b, sep="") {
    if (a == "") {
        return (b)
    } else {
        return (paste(a,b,sep=sep))
    }
}

if ( is.null(opts$systemname) ) {
    systemname <- ""
} else {
    systemname <- opts$systemname
}

allData <- readData(args)
filteredData <- allData

filterName <- ""
filterDisplayName <- ""

if (opts$annotatedOnly) {
    filteredData <- subset(filteredData, AnnotatedOnly != 0)
    filterName <- appendUnlessEmpty(filterName, "annotated", sep=".")
    filterDisplayName <- appendUnlessEmpty(filterDisplayName, "annotated", sep=", ")
} else {
    filteredData <- subset(filteredData, AnnotatedOnly == 0)
}

if (opts$changedOnly) {
    filteredData <- subset(filteredData, ChangedOnly != 0)
    filterName <- appendUnlessEmpty(filterName, "changed", sep=".")
    filterDisplayName <- appendUnlessEmpty(filterDisplayName, "changed", sep=", ")
} else {
    filteredData <- subset(filteredData, ChangedOnly == 0)
}

if (filterName == "") {
    filterName <- "all"
    filterDisplayName <- "all"
}

titleExtra <- paste("(", filterDisplayName, " functions", ")", sep="")
title <- paste(systemname, titleExtra, sep=" ")

if ( !is.null(opts$output) ) {
    outputFn <- opts$output
} else {
    outputFn <- paste("correlation-heatmap-", systemname, "-", filterName, ".pdf", sep="")
}

##library(plotly)

## Default width and height of PDFs is 7 inches
pdf(file=outputFn,width=7,height=7)

row.names(filteredData) <- filteredData$Independent
filteredData <- filteredData[,5:8]

library(gplots)
library(RColorBrewer)

mat <- data.matrix(filteredData)

##heatmap <- heatmap.2(mat,
##                   ,cellnote = mat
##                 , Rowv=NA, Colv=NA
##                 , col = heat.colors(256),
##                   scale="column", margins=c(12,12))

matRound <- round(mat, 2)

## See http://sebastianraschka.com/Articles/heatmaps_in_r.html

numBreaks <- 16

myPalette <- colorRampPalette(c("blue", "green", "yellow", "red"))(n = numBreaks)

colBreaks <- seq(-0.7, 0.6, length=numBreaks+1)

hm <- heatmap.2(mat,
                cellnote = matRound,  # data set for cell labels
                main = title, # heat map title
                notecol="black",      # change font color of cell labels to black
                density.info="none",  # turns off density plot inside color legend
                trace="none",         # turns off trace lines inside the heat map
                margins =c(12,12),     # widens margins around plot
                col=myPalette,       # use on color palette defined earlier
                breaks=colBreaks,    # enable color transition at specified limits
                dendrogram="none",    # don't draw a row dendrogram
                Colv="NA",             # turn off column clustering
                Rowv=FALSE		# disable row reordering
)

invisible(dev.off())

cat(outputFn,"\n",sep="")
