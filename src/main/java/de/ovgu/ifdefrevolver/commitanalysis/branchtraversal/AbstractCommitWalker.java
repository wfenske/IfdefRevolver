package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import org.apache.log4j.Logger;

import java.util.*;


public abstract class AbstractCommitWalker {
    private static Logger LOG = Logger.getLogger(AbstractCommitWalker.class);
    protected CommitsDistanceDb commitsDistanceDb;
    protected Commit currentCommit;
    protected Queue<Commit> next;
    protected BitSet processed;

    public AbstractCommitWalker(CommitsDistanceDb commitsDistanceDb) {
        this.commitsDistanceDb = commitsDistanceDb;
    }

    public void processCommits() {
        final int numAllCommits = getNumAllCommits();
        processed = new BitSet(numAllCommits);
        next = new PriorityQueue<>(commitsDistanceDb.getRoots().size(), Commit.BY_TIMESTAMP_FIRST);
        next.addAll(commitsDistanceDb.getRoots());

        while (!next.isEmpty()) {
            this.currentCommit = getNextProcessableCommit();
            processCurrentCommit();
            markCurrentCommitAsProcessed();

            for (Commit child : currentCommit.children()) {
                offerIfNew(child);
            }
        }

        final int numUnprocessed = numAllCommits - processed.cardinality();
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
        List<Commit> unprocessableCommits = new ArrayList<>();
        for (int i = 0; i < sz; i++) {
            Commit nextCommit = next.poll(); // cannot be null due to preconditions of this method
            if (isProcessable(nextCommit)) {
                unprocessableCommits.stream().forEach(c -> next.offer(c));
                return nextCommit;
            } else {
                unprocessableCommits.add(nextCommit);
            }
        }
        throw new RuntimeException("None of the next commits is processable");
    }

    protected void onUnprocessedCommitsRemain(int numUnprocessed) {
        throw new RuntimeException(numUnprocessed + " unprocessed commit(s) remain(s)");
    }

    protected void onAllCommitsProcessed() {
        LOG.info("Successfully processed all " + processed.cardinality() + " commits.");
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
        return processed.get(commit.key);
    }

    protected void markCurrentCommitAsProcessed() {
        processed.set(currentCommit.key);
    }

    protected abstract void processCurrentCommit();

    private void offerIfNew(Commit commit) {
        if (!isCommitProcessed(commit) && !next.contains(commit)) {
            next.offer(commit);
        }
    }
}
