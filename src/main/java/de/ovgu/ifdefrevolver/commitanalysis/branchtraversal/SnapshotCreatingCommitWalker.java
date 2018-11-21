package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;

public class SnapshotCreatingCommitWalker extends AbstractCommitWalker {
    public SnapshotCreatingCommitWalker(CommitsDistanceDb commitsDistanceDb) {
        super(commitsDistanceDb);
    }

    @Override
    protected void processCurrentCommit() {

    }
}
