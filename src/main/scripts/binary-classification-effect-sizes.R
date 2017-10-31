#!/usr/bin/env Rscript

### 
### $ csvsql -d',' -q '"' --tables a,b --query 'select a.System, a.fl fl, b.fl flnorm, a.fc fc, b.fc fcnorm, a.cnd cnd, b.cnd cndnorm, a.neg neg, b.neg negnorm from a join b on a.system=b.system' reorged-fisher-COMMITS.csv reorged-fisher-COMMITSratio.csv > reorged-fisher-COMMITSandCOMMITSratio.csv
###
### $ csvsql -d',' -q '"' --tables a,b --query 'select a.System, a.fl fl, b.fl flnorm, a.fc fc, b.fc fcnorm, a.cnd cnd, b.cnd cndnorm, a.neg neg, b.neg negnorm from a join b on a.system=b.system' reorged-fisher-LCH.csv reorged-fisher-LCHratio.csv > reorged-fisher-LCHandLCHratio.csv
###
### $ binary-classification-effect-sizes.R -o reorged-fisher-COMMITSandCOMMITSratio.pdf reorged-fisher-COMMITSandCOMMITSratio.csv -d 'Commits per Function, w/o and w/ Normalization' && open reorged-fisher-COMMITSandCOMMITSratio.pdf
###
### $ binary-classification-effect-sizes.R -o reorged-fisher-LCHandLCHratio.pdf reorged-fisher-LCHandLCHratio.csv -d 'Lines Changed per Function, w/o and w/ Normalization' && open reorged-fisher-LCHandLCHratio.pdf

library(optparse)

printf  <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

options <- list(
    make_option(c("-o", "--output")
              , help="Output filename."
                , default = NULL
                )
  , make_option(c("-d", "--dependent")
              , help="Pretty name of the dependent variable."
              , default = NULL
                )
##  , make_option(c("--ymax")
##              , help="Maximum value on y-axis.  Default is determined dynamically by R depending on the data."
##              , default = NULL
##              , type = "numeric"
##                )
##  , make_option(c("-X", "--no-x-labels")
##              , dest="noXLabels"
##              , action="store_true"
##              , default=FALSE
##              , help="Omit name and labels of y axis [default %default]"
##                )
  , make_option(c("-T", "--no-title")
              , dest="noTitle"
              , action="store_true"
              , default=FALSE
              , help="Omit putting system name as the title of the plot [default %default]"
                )
)

args <- parse_args(OptionParser(
    description = "Create box plots from the effect sizes given in the CSV file."
  , usage = "%prog [options] file"
  , option_list=options)
  , positional_arguments = c(1, 1))
opts <- args$options

readData <- function(commandLineArgs) {
    fns <- commandLineArgs$args
    if ( length(fns) != 1 ) {
        stop("Missing input files.  Either specify explicit input files or specify the name of the project the `--project' option (`-p' for short).")
    }
    dataFn <- fns[1]
    eprintf("DEBUG: Reading data from %s\n", dataFn)
    result <- read.csv(dataFn, header=TRUE, sep=",")
    eprintf("DEBUG: Sucessfully read data.\n")
    return (result)
}

if (is.null(opts$output)) {
    stop("Missing output file name.  Specify via `--output' (`-o').")
}
outputFn <- opts$output

allData <- readData(args)

### Begin plot creation

maxIx <- length(colnames(allData))

width <- 6

if (maxIx == 5) {
    colnames(allData)[2] <- 'fl>0'
    colnames(allData)[3] <- 'fc>1'
    colnames(allData)[4] <- 'cnd>0'
    colnames(allData)[5] <- 'neg>0'
} else {
    colnames(allData)[2] <- 'fl>0'
    colnames(allData)[3] <- 'fl>0 (n)'
    colnames(allData)[4] <- 'fc>1'
    colnames(allData)[5] <- 'fc>1 (n)'
    colnames(allData)[6] <- 'cnd>0'
    colnames(allData)[7] <- 'cnd>0 (n)'
    colnames(allData)[8] <- 'neg>0'
    colnames(allData)[9] <- 'neg>0 (n)'
    width <- width * 2
}

pdf(file=outputFn, width=1+width)##,width=7.5,height=3.6)

##x <- allData$FLgrouped
##y <- allData$LOC

##if (opts$independent == "FC") {
##    yLab <- "Feature Constants (FC)"
##} else if (opts$independent == "FL") {
##    yLab <- "Feature Locations (FL)"
##} else if (opts$independent == "ND") {
##    yLab <- "Nesting Depth (ND)"
##} else {
##    stop(paste("Invalid independent variable name: ", opts$independent))
##}
###yLab <- opts$independent

yLims <- c(0,0.6)

##yLab <- opts$yLab

# Decrease `horizontalScale' to move boxes in boxplot closer together
##horizontalScale <- 0.75
##xAxisPositions <- seq(1,by=horizontalScale,length.out=opts$divisions)
##xLims <- c(xAxisPositions[1]-0.5,xAxisPositions[opts$divisions] + 0.3 * horizontalScale)

##if (opts$noXLabels) {
##    xLab <- ""
##    xAxt <- "n"
##} else {
##    xLab <- "LOC"
##    xAxt <- NULL # default value
##}

##colors <- topo.colors(opts$divisions)

##txtScale <- 1.2

boxplot(allData[2:maxIx]
            #, cex=txtScale
            ##, cex.lab=txtScale
            ##, cex.axis=txtScale # size of value labels on x & y axis
            #, cex.main=1
            #, cex.sub=1
            #, xaxt=xAxt
            #, xlab=xLab
            #, yaxt=yAxt
            , ylab="Cliff's Delta"
            , main=(if (opts$noTitle) NULL else paste("Group Differences wrt.", opts$dependent))
            #, outline=FALSE
            #, varwidth=T
            #, at=xAxisPositions
            ##, xlim=xLims
            , ylim=yLims
            #, horizontal = TRUE
            , col = c('white', 'gray')
        )

##text(1:5,rep(min(y),5),paste("n=",tapply(y,x,length)) )

## Add number of observations per box plot.  Taken from
## https://stat.ethz.ch/pipermail/r-help/2008-January/152994.html
##mtext(paste("(n=", bp$n, ")", sep = ""), at = xAxisPositions, line = 1.9,
##      side = 2
##    , cex = 0.8 # scale text size
##      )

##legend("bottomright", ##inset=.02
##     , title="Number of functions"
##     , c(paste("", bp$n, sep = ""))
##     , fill=colors, horiz=TRUE, cex=txtScale)

invisible(dev.off())

cat(outputFn,"\n",sep="")
