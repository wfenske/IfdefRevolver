#!/usr/bin/env Rscript

library(optparse)

cmdArgs <- commandArgs(trailingOnly = FALSE)
file.arg.name <- "--file="
script.fullname <- sub(file.arg.name, "",
                       cmdArgs[grep(file.arg.name, cmdArgs)])
script.dir <- dirname(script.fullname)
source(file.path(script.dir, "regression-common.R"))

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `<projec-name>/results/joint_data.rds' below the current working directory."
              , default = NULL
                )
    
  , make_option(c("-n", "--name")
              , help="Pretty name of project, to be added to the plot."
              , default = NULL
                )
    
  , make_option(c("-d", "--divisions")
              , help="Number of divisions to make. [default=%default]"
              , default = 3
                )
  , make_option(c("-o", "--output")
              , help="Output filename."
                , default = NULL
                )
  , make_option(c("-i", "--independent")
              , help="Name of the independent variable. [default=%default]"
              , default = "FL"
                )
  , make_option(c("--ymax")
              , help="Maximum value on y-axis.  Default is determined dynamically by R depending on the data."
              , default = NULL
              , type = "numeric"
                )
  , make_option(c("-X", "--no-x-labels")
              , dest="noXLabels"
              , action="store_true"
              , default=FALSE
              , help="Omit name and labels of y axis [default %default]"
                )
  , make_option(c("-T", "--no-title")
              , dest="noTitle"
              , action="store_true"
              , default=FALSE
              , help="Omit putting system name as the title of the plot [default %default]"
                )
)

args <- parse_args(OptionParser(
    description = "Compare SLOC of non-annotated and annotated functions in a project. The project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

getPrettySystemName <-function() {
    if (is.null(opts$name)) {
        stop("Missing pretty name.  Specify via `--name' (`-n').")
    }
}

allData <- readData(args)

isLoacRatio <- opts$independent == "LOACratio"

if (isLoacRatio) {
    stepWidthPercent <- 10.0
    threshold <- (opts$divisions - 1) * (stepWidthPercent / 100.0)
    aboveThresholdLabel <- sprintf("%d%%+", as.integer(round(threshold * 100)))

    findUpperBound <- function(v, bound) {
        ifelse(v < (bound * stepWidthPercent), bound, findUpperBound(v, bound+1))
    }
    
    valGroup <- function(v) {
        upper <- findUpperBound(v * 100, 1)
        lower <- upper -1
        upperPercent <- as.integer(round(upper*stepWidthPercent))
        lowerPercent <- as.integer(round(lower*stepWidthPercent))
        
        ifelse(v < threshold
             , sprintf("%d...%d%%"
                     , lowerPercent
                     , upperPercent)
             , aboveThresholdLabel)
    }
} else {
    threshold <- opts$divisions - 1
    aboveThresholdLabel <- paste((threshold), "+", sep="")
    valGroup <- function(v) {
        ifelse(v < threshold, sprintf("%d", v), aboveThresholdLabel)
    }
}

##allData$grouped <- mapply(valGroup, eval(parse(text=paste("allData", opts$independent, sep="$"))))
allData$grouped <- valGroup(allData[,opts$independent])

if (is.null(opts$output)) {
    outputFn <- paste("LOC_", getPrettySystemName(), ".pdf", sep="")
} else {
    outputFn <- opts$output
}

### Begin plot creation

##pdf(file=outputFn,width=7.5,height=3.6)
cairo_pdf(file=outputFn,width=7.5,height=3.6)

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

if (isLoacRatio) {
    yLab <- expression(frac(italic(loac), italic(loc))) ##"loac/loc"
} else {
    yLab <- tolower(opts$independent)
}

if (is.null(opts$ymax)) {
    yLims <- c()
} else {
    yLims <- c(0,as.integer(round(opts$ymax)))
}

# Decrease `horizontalScale' to move boxes in boxplot closer together
horizontalScale <- 0.9
xAxisPositions <- seq(1,by=horizontalScale,length.out=opts$divisions)
##xLims <- c(xAxisPositions[1]-0.5,xAxisPositions[opts$divisions] + 0.3 * horizontalScale)

if (opts$noXLabels) {
    xLab <- ""
    xAxt <- "n"
} else {
    xLab <- "loc"
    xAxt <- NULL # default value
}

##colors <- topo.colors(opts$divisions)

## color-blind-friendly palette (palettes source: http://jfly.iam.u-tokyo.ac.jp/color/)
##
## Roughly: 1=gray, 2=orange, 3=light blue, 4=green, 5=yellow, 6=darkblue, 7=red, 8=magenta
##
## See https://www.datanovia.com/en/blog/ggplot-colors-best-tricks-you-will-love/ for a picture
COLOR_PALETTE <- c("#999999", "#E69F00", "#56B4E9", "#009E73",
                   "#F0E442", "#0072B2", "#D55E00", "#CC79A7")
colorOffset <- 4
colors <- COLOR_PALETTE[colorOffset:(colorOffset + opts$divisions - 1)]

txtScale <- 1.3

## Format of margins is c(bottom, left, top, right)
margins <- par()$mar
margins[2] <- margins[2] + 6.2 ## increase left margin
margins[3] <- margins[3] - 4 ## decrease top margin
##if (opts$noXLabels) {
##    margins[1] <- margins[1] - 5 ## decrease bottom margin
##} else {
    margins[1] <- margins[1] - 1.3 ## decrease bottom margin
##}
margins[4] <- margins[4] - 1.8 ## decrease right margin

par(mar = margins)

cex.lab <- 1.4*txtScale

value.lab.scale <- 1.3 * txtScale

bp <- invisible(boxplot(LOC ~ grouped
            , data=allData
            , cex=txtScale
            , cex.lab=cex.lab
            , cex.axis=value.lab.scale # size of value labels on x & y axis
            #, cex.main=1
            #, cex.sub=1
            , xaxt=xAxt
            #, yaxt=yAxt
            , main=(if (opts$noTitle) NULL else getPrettySystemName())
            , outline=FALSE
            , varwidth=T
            , at=xAxisPositions
            ##, xlim=xLims
            , ylim=yLims
            , horizontal = TRUE
            , col = colors
            , las = 1
              ))

ycex <- cex.lab

if (is.null(xAxt)) {
    mtext(xLab, side=1, line=2.7, cex=ycex
        , font = 3 # 3=italics
        , family = "serif")
}

if (isLoacRatio) {
    mtext(yLab, side=2, line=7, cex=ycex, las=1
        , font = 3 # 3=italics
        , family = "serif")
} else {
    mtext(yLab, side=2, line=2.5, cex=ycex, las=1
        , font = 3 # 3=italics
        , family = "serif")
}

##title(line=ifelse(isLoacRatio, 5.5, 3), cex.lab=cex.lab)

##text(1:5,rep(min(y),5),paste("n=",tapply(y,x,length)) )

## Add number of observations per box plot.  Taken from
## https://stat.ethz.ch/pipermail/r-help/2008-January/152994.html
##mtext(paste("(n=", bp$n, ")", sep = ""), at = xAxisPositions, line = 1.9,
##      side = 2
##    , cex = 0.8 # scale text size
##      )

legend("bottomright", ##inset=.02
     , title="Number of functions per box"
       ##, c(paste("", bp$n, sep = ""))
     , format(bp$n, big.mark=",")
     , fill=colors, horiz=TRUE, cex=txtScale)

invisible(dev.off())

cat(outputFn,"\n",sep="")
