#!/usr/bin/env Rscript

library(optparse)

options <- list(
    make_option(c("-o", "--output"),
                help="Name of output file.  Default is name of input file without filetype extension and with \".pdf\" added.")
    , make_option(c("-Y", "--noyaxislabels"),
                  action="store_true", default=FALSE,
                  help="Omit name and labels of y axis [default %default]")
    , make_option(c("-d", "--delimiter"),
                  default=",",
                  help="Column delimiter, e.g. `,' or `;'. [default: %default]")
    , make_option(c("-s", "--smell"),
                default="ANY",
                  help="Name of smell.  Valid values are  \"ANY\" (any smell), \"AB\" (Annotation Bundle), \"AF\" (Annotation File), \"LF\" (Large Feature). Furthermore, the combinations \"ABorAF\", \"ABandAF\", \"ABorLF\", \"ABandLF\", \"AForLF\", \"AFandLF\" are supported, as well as \"ANY2\" (at least two smells, possibly of the same kind), \"ANY2Distinct\" (at least two different smells), \"AB2\" (at least two Annotation Bundles), \"LF2\" (at least two Large Features). [default: %default]")
    , make_option(c("-n", "--systemname"),
                  default=NULL,
                  help="Name of the system; will be guessed from the input filename if omitted")
)

args <- parse_args(OptionParser(usage = "%prog [options] file", option_list=options), positional_arguments = 1)
opts <- args$options
inputFn <- args$args

if ( !is.null(opts$output) ) {
    outputFn <- opts$output
} else {
    outputFn <- paste(sub("(.*)\\.[^./]*$", "\\1", inputFn), "pdf", sep=".")
}

#cat(paste("inputFn=", inputFn, "; outputFn=", outputFn, sep=""))
#cat("\n")

#stop("")

#inputFn <- commandArgs(trailingOnly=TRUE)[1] # should be overview.csv or overviewSize.csv

if ( !is.null(opts$systemname) ) {
    systemname <- opts$systemname
} else {
    systemdir <- dirname(inputFn)
    systemname <- basename(systemdir)
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

data <- read.csv(file=inputFn, head=TRUE, sep=opts$delimiter)

## <smellkind>_FS	# files w/ smells
## <smellkind>_FNS	# files w/o smells
## <smellkind>_BS	# number of bug fixes to smelly files
## <smellkind>_BNS	# number of bug fixes to non-smelly files

## <smellkind>_FSB	# files w/ the smell w/ at least one bug fix
## <smellkind>_FSNB	# files w/ the smell but w/o any bug fixes
## <smellkind>_FNSB	# files w/o the smell w/ at least one bug fix
## <smellkind>_FNSNB	# files w/o the smell and w/o any bug fixes

## <smellkind>_SLOCMeanS
## <smellkind>_SLOCMeanNS
## <smellkind>_SLOCMedianS
## <smellkind>_SLOCMedianNS

# number of files w/ the smell
ixSmellyFiles           <- which( colnames(data)==paste(opts$smell,"FS" ,sep="_"))
# number of files w/o the smell
ixNonSmellyFiles        <- which( colnames(data)==paste(opts$smell,"FNS",sep="_"))

# number of fixes to files w/ the smell
ixSmellyFixes    <- which( colnames(data)==paste(opts$smell,"BS" ,sep="_"))
# number of fixes to files w/o the smell
ixNonSmellyFixes <- which( colnames(data)==paste(opts$smell,"BNS",sep="_"))

# number files w/ fixes and w/ the smell a.k.a. fixed/smelly
ixFixedSmellyFiles       <- which( colnames(data)==paste(opts$smell,"FSB" , sep="_"))
# number files w/ fixes and w/o the smell a.k.a. fixed/clean
ixFixedNonSmellyFiles    <- which( colnames(data)==paste(opts$smell,"FNSB", sep="_"))
# number files w/o fixes and w/ the smell a.k.a. non-fixed/smelly
ixNonFixedSmellyFiles <- which( colnames(data)==paste(opts$smell,"FSNB",sep="_"))
# number files w/o fixes and w/o the smell a.k.a. non-fixed/clean
ixNonFixedNonSmellyFiles <- which( colnames(data)==paste(opts$smell,"FNSNB",sep="_"))

ratioOfSmellyFiles    <- data[,ixSmellyFiles]   /(data[,ixSmellyFiles] + data[,ixNonSmellyFiles])
ratioOfNonSmellyFiles <- data[,ixNonSmellyFiles]/(data[,ixSmellyFiles] + data[,ixNonSmellyFiles])

ratioOfSmellyFixes    <- data[,ixSmellyFixes]   /(data[,ixSmellyFixes] + data[,ixNonSmellyFixes])
ratioOfNonSmellyFixes <- data[,ixNonSmellyFixes]/(data[,ixSmellyFixes] + data[,ixNonSmellyFixes])

ratioOfFixedSmellyFiles       <- data[,ixFixedSmellyFiles]      /(data[,ixSmellyFiles] + data[,ixNonSmellyFiles])
ratioOfFixedNonSmellyFiles    <- data[,ixFixedNonSmellyFiles]   /(data[,ixSmellyFiles] + data[,ixNonSmellyFiles])
ratioOfNonFixedNonSmellyFiles <- data[,ixNonFixedNonSmellyFiles]/(data[,ixSmellyFiles] + data[,ixNonSmellyFiles])

# Default width and height of PDFs is 7 inches
pdf(file=outputFn,width=7,height=4)

par(mfrow=c(1,1))
## % Smelly files
plot(ratioOfSmellyFiles, type="l", col="red", ylim=c(0,1),
     main=systemname,
     xlab=xAxisName,
     ylab=yAxisName, yaxt='n',
     cex.lab=axisNameFontScale,
     cex.main=plotNameFontScale)
## % Non-Smelly files
lines(ratioOfNonSmellyFiles, col="blue")
axis(2, at=yLabels, labels=yAxisLabels, las=1)
##par(new=TRUE)
## Fixes to smelly files
##plot(ratioOfSmellyFixes, type="l", col="red", ylim=c(0,1),
##     xlab="", xaxt='n',
##     ylab="", yaxt='n',
##     lty=2)
##lines(ratioOfNonSmellyFixes, col="blue", lty=2)
##lines(ratioOfNonSmellyFiles, pch=22, lty=2)
lines(ratioOfFixedSmellyFiles,       col="red",  lty=2)
lines(ratioOfNonFixedNonSmellyFiles, col="blue", lty=2)

invisible(dev.off())
