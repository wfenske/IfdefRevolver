## Taken from https://stackoverflow.com/questions/7828248/make-y-axis-logarithmic-in-histogram-using-r

plotLogHist <- function(x) {
    hist.data = hist(x, plot=F)
    hist.data$counts = log10(hist.data$counts + 1)
    
    dev.new(width=4, height=4)
    hist(x)

    dev.new(width=4, height=4)
    plot(hist.data, ylab='log10(Frequency)')
}
