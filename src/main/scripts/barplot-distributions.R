#!/usr/bin/env Rscript

### Usage examples:
###
### barplot-distributions.R -p busybox -v commits -o /tmp/busybox-commits.pdf
###
### p=busybox; var=commits; open $(barplot-distributions.R -p $p -v $var -o /tmp/${p}-${var}.pdf )

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
###  , make_option(c("-n", "--name")
###              , help="Pretty name of project, if present will be inserted as the plot title."
###              , default = NULL
###                )
  , make_option(c("-o", "--output")
              , help="Output filename (mandatory argument)"
                , default = NULL
                )
  , make_option(c("-v", "--variable")
              , help="Name of the variable (mandatory argument)"
              , default = NULL
                )
  , make_option(c("--ymax")
              , help="Maximum value on y-axis.  Default is determined dynamically by R depending on the data."
              , default = NULL
              , type = "numeric"
                )
)

args <- parse_args(OptionParser(
    description = "Compare SLOC of non-annotated and annotated functions in a project. The project must be specified via the `--project' (`-p') option."
  , usage = "%prog [options] [file]"
  , option_list=options)
  , positional_arguments = c(0, 1))
opts <- args$options

allData <- readData(args)

if (is.null(opts$output)) {
    stop("Missing output filename; specify via `--output' (`-o').")
}

outputFn <- opts$output

### Begin plot creation

##pdf(file=outputFn,width=7.5,height=3.6)
cairo_pdf(file=outputFn,width=5,height=5)

if (is.null(opts$ymax)) {
    yLims <- c(1, 10^ceiling(log10(nrow(allData))))
} else {
    yLims <- c(1, as.integer(round(opts$ymax)))
}

barplot(table(allData[toupper(opts$variable)]),
        log="y",
        ylim=yLims,
        yaxt="n")

mtext(side=1, line=2, text=tolower(opts$variable), font = 3, family = "serif")

axis(2, las = 1, ##font = varFontFace, family = "serif",
     at     = c(1,                1e+01,            1e+02,            1e+03,            1e+04,            1e+05,            1e+06,            1e+07,            1e+08),
     labels = c(expression(10^0), expression(10^1), expression(10^2), expression(10^3), expression(10^4), expression(10^5), expression(10^6), expression(10^7), expression(10^8)))

invisible(dev.off())

cat(outputFn,"\n",sep="")
