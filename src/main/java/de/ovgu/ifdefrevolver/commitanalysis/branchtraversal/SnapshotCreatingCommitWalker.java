package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;

public class SnapshotCreatingCommitWalker extends AbstractCommitWalker {
    private int commitIndex;
    private int snapshotIndex;
    private final int snapshotSize;

    public SnapshotCreatingCommitWalker(CommitsDistanceDb commitsDistanceDb) {
        super(commitsDistanceDb);
        this.snapshotSize = 100;
    }

    @Override
    public void processCommits() {
        this.commitIndex = 0;
        this.snapshotIndex = 0;
        super.processCommits();
    }

    @Override
    protected void processCurrentCommit() {
        this.commitIndex++;
    }
}
