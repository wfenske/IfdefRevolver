#!/usr/bin/env Rscript

library(optparse)

printf <- function(...) cat(sprintf(...), sep='', file=stdout())
eprintf <- function(...) cat(sprintf(...), sep='', file=stderr())

options <- list()

args <- parse_args(OptionParser(
    description = "Count the number of changed/unchanged functions in one or several IfdefRevolver projects."
  , usage = "%prog PROJECT_PATH ..."
  , option_list=options)
  , positional_arguments = c(1, Inf))
opts <- args$options
sysNames <- args$args

getChangeOdds <- function(projectPath) {
    sysName <- basename(projectPath)
    rdsName <- file.path(projectPath, "results", "joint_data.rds")
    eprintf("DEBUG: Reading data for %s\n", sysName)
    df <- readRDS(rdsName)
    
    nChanged      <- nrow(subset(df, COMMITS > 0))
    nUnchanged    <- nrow(subset(df, COMMITS == 0))
    meanCommits   <- mean(df$COMMITS)
    medianCommits <- median(df$COMMITS)
    sdCommits     <- sd(df$COMMITS)
    meanLch       <- mean(df$LCH)
    medianLch     <- median(df$LCH)
    sdLch         <- sd(df$LCH)
    
    rm(df)
    
    or <- nChanged / nUnchanged
    
    likelihood <- nChanged / (nChanged + nUnchanged)
    eprintf("DEBUG: %s,%.2f,%d,%d,%.2f,%.2f\n", sysName, meanCommits, nChanged, nUnchanged, or, likelihood)
    data.frame(system        = sysName
             , meancommits   = meanCommits
             , mediancommits = medianCommits
             , sdcommits     = sdCommits
             , meanlch       = meanLch
             , medianlch     = medianLch
             , sdlch         = sdLch
             , changed       = nChanged
             , unchanged     = nUnchanged
             , or            = or
             , likelihood    = likelihood)
}

eprintf("DEBUG: system,meancommits,changed,unchanged,or,likelihood\n")
rows <- lapply(sysNames, getChangeOdds)
changeDf <- do.call("rbind", rows)
write.csv(changeDf
         ,row.names=FALSE
         ,fileEncoding="utf-8"
         ,quote=FALSE
         ,file=stdout())
