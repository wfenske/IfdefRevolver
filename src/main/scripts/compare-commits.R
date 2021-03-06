#!/usr/bin/env Rscript

library(optparse)

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  The input R data is expected to reside in `<projec-name>/results/allDataAge.rdata' below the current working directory."
              , default = NULL
                )
    
  , make_option(c("-n", "--name")
              , help="Pretty name of project, to be added to the plot."
              , default = NULL
                )
    
  , make_option(c("-d", "--divisions")
              , help="Number of divisions to make."
              , default = 2
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
    description = "Compare COMMITS of non-annotated and annotated functions in a project. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

readData <- function(commandLineArgs) {
    fns <- commandLineArgs$args
    if ( length(fns) == 1 ) {
        dataFn <- fns[1]
    } else {
        opts <- commandLineArgs$options
        if ( is.null(opts$project) ) {
            stop("Missing input files.  Either specify explicit input files or specify the name of the project the `--project' option (`-p' for short).")
        }
        dataFn <-  file.path(opts$project, "results", "allDataAge.rdata")
    }
    write(paste("DEBUG: Reading data from ", dataFn, sep=""), stderr())
    result <- readRDS(dataFn)
    write("DEBUG: Sucessfully read data.", stderr())
    return (result)
}

if (is.null(opts$name)) {
    stop("Missing pretty name.  Specify via `--name' (`-n').")
}

allData <- readData(args)

threshold <- opts$divisions - 1
aboveThreshold <- paste((threshold), "+", sep="")
labels <- c("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13")

valGroup <- function(v) {
    if (v < threshold) {
        return (labels[[ v + 1]]);
    } else {
        return (aboveThreshold);
    }
}

allData$grouped <- mapply(valGroup, eval(parse(text=paste("allData", opts$independent, sep="$"))))

if (is.null(opts$output)) {
    outputFn <- paste("COMMITS_", opts$name, ".pdf", sep="")
} else {
    outputFn <- opts$output
}

### Begin plot creation

pdf(file=outputFn)

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
xLab <- opts$independent

if (is.null(opts$ymax)) {
    yLims <- c()
} else {
    yLims <- c(0,as.integer(round(opts$ymax)))
}

# Decrease `horizontalScale' to move boxes in boxplot closer together
horizontalScale <- 1 #0.75
xAxisPositions <- seq(1,by=horizontalScale,length.out=opts$divisions)
##xLims <- c(xAxisPositions[1]-0.5,xAxisPositions[opts$divisions] + 0.3 * horizontalScale)

if (opts$noXLabels) {
    yLab <- ""
    yAxt <- "n"
} else {
    yLab <- "COMMITS"
    yAxt <- NULL # default value
}

colors <- topo.colors(opts$divisions)

##txtScale <- 1.2

bp <- invisible(boxplot(COMMITS ~ grouped
            , data=allData
#            , cex=txtScale
#            , cex.lab=txtScale
#            , cex.axis=txtScale # size of value labels on x & y axis
            #, cex.main=1
            #, cex.sub=1
#            , xaxt=xAxt
            , xlab=xLab
            #, yaxt=yAxt
            , ylab=yLab
            , main=(if (opts$noTitle) NULL else opts$name)
#            , outline=FALSE
#            , varwidth=T
            , at=xAxisPositions
            ##, xlim=xLims
            , ylim=yLims
#            , horizontal = TRUE
            , col = colors
              ))
##text(1:5,rep(min(y),5),paste("n=",tapply(y,x,length)) )

## Add number of observations per box plot.  Taken from
## https://stat.ethz.ch/pipermail/r-help/2008-January/152994.html
##mtext(paste("(n=", bp$n, ")", sep = ""), at = xAxisPositions, line = 1.9,
##      side = 2
##    , cex = 0.8 # scale text size
##      )

#legend("bottomright", ##inset=.02
#     , title="Number of functions"
#     , c(paste("", bp$n, sep = ""))
#     , fill=colors, horiz=TRUE, cex=txtScale)

invisible(dev.off())

cat(outputFn,"\n",sep="")
