#!/usr/bin/env Rscript

### Performs linear regression the totals over all snapshots of a system
###
### Input files are the snapshot files under Correlated/

## Example by Fisher:

library(optparse)

options <- list(
    make_option(c("-p", "--project")
              , help="Name of the project whose data to load.  We expect the input R data to reside in `results/<projec-name>/allData.rdata' below the current working directory."
              , default = NULL
                )
    
  , make_option(c("-n", "--name")
              , help="Pretty name of project, to be added to the plot."
              , default = NULL
                )
    
  , make_option(c("-d", "--divisions")
              , help="Number of divisions to make."
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
)

args <- parse_args(OptionParser(
    description = "Compare SLOC of non-annotated and annotated functions in a project. If no input R data set is given, the project must be specified via the `--project' (`-p') option."
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
        dataFn <-  file.path("results", opts$project, "allData.rdata")
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
    outputFn <- paste("LOC_", opts$name, ".pdf", sep="")
} else {
    outputFn <- opts$output
}

### Begin plot creation

pdf(file=outputFn,width=6,height=4)

##x <- allData$FLgrouped
##y <- allData$LOC

if (opts$independent == "FC") {
    xlab <- "Number of feature constants (FC)"
} else if (opts$independent == "FL") {
    xlab <- "Number of #ifdefs (FL)"
} else if (opts$independent == "ND") {
    xlab <- "Aggregated nesting depth (ND)"
} else {
    stop(paste("Invalid independent variable name: ", opts$independent))
}

bp <- invisible(boxplot(LOC ~ grouped
            , data=allData
            , cex=1, cex.axis=1.1, cex.main=1, cex.sub=1
            , xlab=xlab
            , ylab="LOC"
            , main=opts$name
            , outline=FALSE
            , varwidth=T
              ))
##text(1:5,rep(min(y),5),paste("n=",tapply(y,x,length)) )

## Add number of observations per box plot.  Taken from
## https://stat.ethz.ch/pipermail/r-help/2008-January/152994.html
mtext(paste("(n=", bp$n, ")", sep = ""), at = seq_along(bp$n), line = 2, side = 1
     , cex = 0.75 # scale text size
      )

##invisible(dev.off())

cat(outputFn,"\n",sep="")
