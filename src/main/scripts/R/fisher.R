#!/usr/bin/env Rscript

### Performs Fisher's test on the totals over all windows of a system
###
### Input files are eiither overview.csv or overviewSize.csv

## Example by Fisher:

##Convictions <- matrix(c(2, 10, 15, 3)
##                      , nrow = 2
##                      , dimnames = list(Twins = c('Dizygotic', 'Monozygotic')
##                                      , Status = c('Convicted', 'Not convicted')))
##
##              Status
## Twins        Convicted Not convicted
## Dizygotic     2             15
## Monozygotic  10              3
##
##              Status
## Twins        Convicted Not convicted
## Dizygotic     dc             dn
## Monozygotic   mc             mn
##
## --> first arg to matrix command has the following form:
##
##     c(dc, mc, dn, mn)

## Table make-up
##
##   xxx    | fixed | not fixed
##   smelly | s-f   |   s-nf
##   clean  | c-f   |   c-nf
##
## --> smelly-fixed, clean-fixed, smelly-not-fixed, clean-not-fixed

library(optparse)

options <- list(
##    make_option(c("-o", "--output"),
##                help="Name of output file.  Default is name of input file without filetype extension and with \".pdf\" added."),
    make_option(c("-s", "--smell"),
                default="ANY",
                help="Name of smell.  Valid values are  \"ANY\" (any smell), \"AB\" (Annotation Bundle), \"AF\" (Annotation File), \"LF\" (Large Feature). Furthermore, the combinations \"ABorAF\", \"ABandAF\", \"ABorLF\", \"ABandLF\", \"AForLF\", \"AFandLF\" are supported, as well as \"ANY2\" (at least two smells, possibly of the same kind), \"ANY2Distinct\" (at least two different smells), \"AB2\" (at least two Annotation Bundles), \"LF2\" (at least two Large Features) . [default: %default]")
    , make_option(c("-n", "--normalize"),
                default=FALSE,
                action="store_true",
                help="Normalize file counts for each window to the average (mean) number of files")
    , make_option(c("-H", "--no_header"),
                default=FALSE,
                action="store_true",
                help="Omit header row")
    , make_option(c("-d", "--delimiter"),
                default=",",
                help="Column delimiter, e.g. `,' or `;'. [default: %default]")
)

args <- parse_args(OptionParser(usage = "%prog [options] file", option_list=options), positional_arguments = 1)
opts <- args$options
inputFn <- args$args

smellName <- opts$smell

data <- read.csv(file=inputFn, head=TRUE, sep=opts$delimiter)

# number of smelly, fixed files
colIxSF  <- which( colnames(data)==paste(smellName,"FSB"  ,sep="_"))
# number of clean (non-smelly), fixed files
colIxCF  <- which( colnames(data)==paste(smellName,"FNSB" ,sep="_"))
# number of smelly, non-fixed files
colIxSNF <- which( colnames(data)==paste(smellName,"FSNB" ,sep="_"))
# number of clean (non-smelly), non-fixed files
colIxCNF <- which( colnames(data)==paste(smellName,"FNSNB",sep="_"))

counts <- data[c(colIxSF,colIxCF,colIxSNF,colIxCNF)]

if ( opts$normalize ) {
    meanSampleSize <- mean(rowSums(counts))
    normalize <- function(row) {
        factor <- meanSampleSize / sum(row)
        scaledRow <- row * factor
        return (round(scaledRow))
    }
    normalizedCounts <- t(apply(counts,1,normalize))
} else {
    normalizedCounts <- counts
}

sums <- colSums(normalizedCounts)
fisherTable <- matrix(sums, nrow=2,
                      dimnames=list(c("Smelly", "Clean"),
                                    c("Fixed", "Not Fixed")))
fisherResults <- fisher.test(fisherTable, alternative = "greater")

OR <- fisherResults$estimate
p.value  <- fisherResults$p.value

if ( OR > 1 ) {
    if ( p.value < 0.05 ) {
        rating <- "++"
    } else {
        rating <- "+~"
    }
} else {
    if ( p.value < 0.05 ) {
        rating <- "--"
    } else {
        rating <- "-~"
    }
}

if ( !opts$no_header ) {
    cat("Smell","Rating","OR","p-value","System\n",sep=";")
}

sysname <- basename(dirname(inputFn))
cat(sprintf(opts$smell,rating,OR,p.value,sysname,fmt="% 3s;%s;%.2f;%.3f;%s\n"))

#fisherResults$estimate
#fisherResults$p.value
