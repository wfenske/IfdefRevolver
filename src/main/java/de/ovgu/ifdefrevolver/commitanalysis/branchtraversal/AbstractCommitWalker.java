package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import org.apache.log4j.Logger;

import java.util.BitSet;
import java.util.PriorityQueue;
import java.util.Queue;


public abstract class AbstractCommitWalker {
    private static Logger LOG = Logger.getLogger(AbstractCommitWalker.class);
    protected CommitsDistanceDb commitsDistanceDb;
    protected Commit currentCommit;
    protected Queue<Commit> next;
    protected BitSet done;

    public AbstractCommitWalker(CommitsDistanceDb commitsDistanceDb) {
        this.commitsDistanceDb = commitsDistanceDb;
    }

    public void processCommits() {
        final int numAllCommits = getNumAllCommits();
        done = new BitSet(numAllCommits);
        next = new PriorityQueue<>(commitsDistanceDb.getRoots().size(), Commit.BY_TIMESTAMP_FIRST);
        next.addAll(commitsDistanceDb.getRoots());

        while (!next.isEmpty()) {
            this.currentCommit = getNextProcessableCommit();
            processCurrentCommit();
            markCurrentCommitAsDone();

            for (Commit child : currentCommit.children()) {
                offerIfNew(child);
            }
        }

        final int numUnprocessed = numAllCommits - done.cardinality();
        if (numUnprocessed != 0) {
            onUnprocessedCommitsRemain(numUnprocessed);
        } else {
            onAllCommitsProcessed();
        }
    }

    protected int getNumAllCommits() {
        return commitsDistanceDb.getCommits().size();
    }

    protected Commit getNextProcessableCommit() {
        int sz = next.size();
        for (int i = 0; i < sz; i++) {
            Commit nextCommit = next.poll(); // cannot be null due to preconditions of this method
            if (isProcessable(nextCommit)) {
                return nextCommit;
            } else {
                next.offer(nextCommit);
            }
        }
        throw new RuntimeException("None of the next commits is processable");
    }

    protected void markCurrentCommitAsDone() {
        done.set(currentCommit.key);
    }

    protected void onUnprocessedCommitsRemain(int numUnprocessed) {
        throw new RuntimeException(numUnprocessed + " unprocessed commit(s) remain(s)");
    }

    protected void onAllCommitsProcessed() {
        LOG.info("Successfully processed all " + done.cardinality() + " commits.");
    }

    protected boolean isProcessable(Commit commit) {
        for (Commit parent : commit.parents()) {
            if (!isCommitProcessed(parent)) {
                //LOG.debug(commit + " cannot be processed: Parent " + parent + " has not yet been processed.");
                return false;
            }
        }

//        if (isCommitSiblingOfUnprocessedMerge(commit)) {
//            return false;
//        }

        return true;
    }

    protected boolean isCommitProcessed(Commit commit) {
        return done.get(commit.key);
    }

    protected abstract void processCurrentCommit();

    private void offerIfNew(Commit commit) {
        if (!isCommitProcessed(commit) && !next.contains(commit)) {
            next.offer(commit);
        }
    }
}
