package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;

class PreMergeBranch extends Branch {
    private final CommitsDistanceDb.Commit mergeCommit;

    public PreMergeBranch(Branch actualParentBranchOfMerge, CommitsDistanceDb.Commit lastCommitBeforeMerge, CommitsDistanceDb.Commit mergeCommit, MoveConflictStats moveConflictStats) {
        super(toBranchArray(actualParentBranchOfMerge), lastCommitBeforeMerge, moveConflictStats, actualParentBranchOfMerge.functionFactory);
        this.mergeCommit = mergeCommit;
        this.split();
    }

    public CommitsDistanceDb.Commit getLastCommitBeforeMerge() {
        return firstCommit;
    }

    public CommitsDistanceDb.Commit getMergeCommit() {
        return mergeCommit;
    }
}
