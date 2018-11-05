package de.ovgu.ifdefrevolver.util;

public abstract class ProgressMonitor {
    protected final int ticksTotal;
    protected int ticksDone = 0;
    protected final float ticksPerReport;
    protected int numberOfCurrentReport = 0;

    public ProgressMonitor(int ticksTotal, float ticksPerReport) {
        this.ticksTotal = ticksTotal;
        this.ticksPerReport = Math.max(ticksPerReport, 1.0f);
    }

    public ProgressMonitor(int ticksTotal) {
        this(ticksTotal, ticksTotal / 100.0f);
    }

    public void increaseDone() {
        ticksDone++;
        if (ticksDone == ticksTotal) {
            numberOfCurrentReport++;
            reportFinished();
        } else if (needIntermediateReport()) {
            numberOfCurrentReport++;
            reportIntermediateProgress();
        }
    }

    protected abstract void reportIntermediateProgress();

    protected abstract void reportFinished();

    protected boolean needIntermediateReport() {
        int numNextReport = Math.round(ticksDone / ticksPerReport);
        return (numNextReport > numberOfCurrentReport);
    }

    protected int percentage() {
        return Math.round(100.0f * ticksDone / Math.max(ticksTotal, 1));
    }

}
