package de.ovgu.ifdefrevolver.util;

public abstract class ProgressMonitor {
    protected final int ticksTotal;
    protected int ticksDone = 0;
    protected final int ticksPerReport;
    protected int numberOfCurrentReport = 0;

    public ProgressMonitor(int ticksTotal, int ticksPerReport) {
        this.ticksTotal = ticksTotal;
        this.ticksPerReport = Math.max(ticksPerReport, 1);
    }

    public ProgressMonitor(int ticksTotal) {
        this(ticksTotal, Math.round(ticksTotal / 100.0f));
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
        return ((ticksDone % ticksPerReport) == 0);
    }

}
