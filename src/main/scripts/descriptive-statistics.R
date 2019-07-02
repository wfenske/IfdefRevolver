#!/usr/bin/env Rscript

library(optparse)

printf <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

options <- list(
    make_option(c("-t", "--type"),
                default="metric",
                help="Type of variables to compute averages of, either `binary' or `metric'. [default: %default]")
)

args <- parse_args(OptionParser(
    description = "Count the number of changed/unchanged functions in one or several IfdefRevolver projects."
  , usage = "%prog --type={binary|metric} PROJECT_PATH ..."
  , option_list=options)
  , positional_arguments = c(1, Inf))
opts <- args$options
sysNames <- args$args

getDescriptiveStatistics <- function(projectPath) {
    sysName <- basename(projectPath)
    rdsName <- file.path(projectPath, "results", "joint_data.rds")
    fnameOutBinaryVariables <- file.path(projectPath, "results", "descriptive-stats-binary.csv")
    fnameOutMetricVariables <- file.path(projectPath, "results", "descriptive-stats-metric.csv")
    
    eprintf("DEBUG: Reading data for %s\n", sysName)
    df <- readRDS(rdsName)
    df <- subset(df, !is.na(MRC) & is.finite(MRC) & !is.na(PC) & is.finite(PC))
    
    df$CHANGED <- df$COMMITS > 0
    df$ANNOTATED <- df$FL > 0

    BVARS <- c('CHANGED'
             , 'ANNOTATED'
               )

    MVARS <- c('FC'
             , 'FL'
             , 'CND'
             , 'NEG'
             , 'LOAC'
             , 'LOACratio'
             , 'LOC'
             , 'MRC'
             , 'PC'
             , 'COMMITS'
             , 'LCH'
               )

    getBinaryStats <- function(varName) {
        occurrences <- table(df[,varName])
        nFalse <- occurrences['FALSE']
        nTrue  <- occurrences['TRUE']
        data.frame(system = sysName
                 , var = varName
                 , nFalse = nFalse
                 , nTrue  = nTrue
                 , or = nTrue / nFalse
                 , likelihood = nTrue / (nTrue + nFalse))
    }

    getMetricStats <- function(varName) {
        vals <- df[,varName]
        data.frame(system = sysName
                 , var = varName
                 , mean = mean(vals)
                 , median = median(vals)
                 , sd = sd(vals))
    }

    if (opts$type == 'binary') {
        binaryStats <- Reduce(rbind, lapply(BVARS, getBinaryStats))
        write.csv(binaryStats
                , file = fnameOutBinaryVariables,
                , row.names=FALSE
                , fileEncoding="utf-8"
                , quote=FALSE)
        printf('%s\n', fnameOutBinaryVariables)
    } else if (opts$type == 'metric') {
        metricStats <- Reduce(rbind, lapply(MVARS, getMetricStats))
        write.csv(metricStats
                , file = fnameOutMetricVariables,
                , row.names=FALSE
                , fileEncoding="utf-8"
                , quote=FALSE)
        printf('%s\n', fnameOutMetricVariables)
    } else {
        msg <- sprintf("Invalid type of variable: %s.  Must be one of `binary' or `metric'.", opts$type)
        stop(msg)
    }
    
    rm(df)
    eprintf("INFO: Successfully computed descriptive statistics for %s\n", sysName)
}

dummy <- lapply(sysNames, getDescriptiveStatistics)
