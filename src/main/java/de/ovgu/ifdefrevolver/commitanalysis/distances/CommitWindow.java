package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.FunctionGenealogy;

import java.util.List;

public class CommitWindow {
    public final List<Snapshot> snapshotsInWindow;
    public final List<FunctionGenealogy> functionsPresentAtSnapshotStart;
    public final List<FunctionGenealogy> functionsChangedInFirstSnapshotButNotPresentAtItsBeginning;

    public CommitWindow(List<Snapshot> snapshotsInWindow, List<FunctionGenealogy> functionsPresentAtSnapshotStart, List<FunctionGenealogy> functionsChangedInFirstSnapshotButNotPresentAtItsBeginning) {
        this.snapshotsInWindow = snapshotsInWindow;
        this.functionsPresentAtSnapshotStart = functionsPresentAtSnapshotStart;
        this.functionsChangedInFirstSnapshotButNotPresentAtItsBeginning = functionsChangedInFirstSnapshotButNotPresentAtItsBeginning;
    }

    public String getWindowStartDateString() {
        return getFirstSnapshot().getStartDateString();
    }

    public Snapshot getFirstSnapshot() {
        return snapshotsInWindow.get(0);
    }

    public int getWindowIndex() {
        return getFirstSnapshot().getIndex();
    }

    public int getNumberOfFunctions() {
        return functionsPresentAtSnapshotStart.size();
    }
}
