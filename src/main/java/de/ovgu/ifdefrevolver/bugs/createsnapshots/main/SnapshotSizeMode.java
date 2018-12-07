package de.ovgu.ifdefrevolver.bugs.createsnapshots.main;

import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.ISnapshot;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by wfenske on 05.04.17.
 */
public enum SnapshotSizeMode {
    /**
     * Commit windows have a fixed size. The number of fixes per window varies.
     */
    COMMITS {
        @Override
        public int defaultSize() {
            return 100;
        }

        @Override
        public void validateSnapshotSize(ISnapshot snapshot, int expectedSize) {
            Set<Commit> commits = snapshot.getCommits();
            int size = commits.size();
            if (size != expectedSize) {
                throw new AssertionError("Snapshot contains " + size + " commits, expected " + expectedSize
                        + ". Commits: " + commits.toString());
            }
        }

        @Override
        public int countRelevantCommits(Collection<Commit> commits) {
            return commits.size();
        }

        @Override
        public Commit skipNRelevantCommits(Iterator<Commit> iter, final int n) {
            if (n < 0) {
                throw new IllegalArgumentException("Number of commits to skip must not be < 0. Received " + n);
            }

            int commitsSeen = 0;
            while (iter.hasNext()) {
                Commit c = iter.next();
                commitsSeen++;
                if (commitsSeen >= n) {
                    return c;
                }
            }

            return null;
        }
    },
    /**
     * Each commit window contains a fixed number of bug-fix commits.  The total number of commits per window therefore
     * varies.
     */
    FIXES {
        @Override
        public int defaultSize() {
            return 50;
        }

        @Override
        public void validateSnapshotSize(ISnapshot snapshot, int expectedSize) {
            Collection<Commit> commits = snapshot.getCommits();
            int size = countRelevantCommits(commits);
            if (size != expectedSize) {
                throw new AssertionError("Snapshot contains " + size + " bug-fix commits, expected " + expectedSize
                        + ". Commits: " + commits);
            }
        }

        @Override
        public int countRelevantCommits(Collection<Commit> commits) {
            int numCommits = 0;
            for (Commit c : commits) {
                if (c.isBugfix()) {
                    numCommits++;
                }
            }
            return numCommits;
        }

        @Override
        public Commit skipNRelevantCommits(Iterator<Commit> iter, final int n) {
            if (n < 0) {
                throw new IllegalArgumentException("Number of bug fixes to skip must not be < 0. Received " + n);
            }

            int fixesSeen = 0;
            while (iter.hasNext()) {
                Commit c = iter.next();
                if (c.isBugfix()) {
                    fixesSeen++;
                    if (fixesSeen >= n) {
                        return c;
                    }
                }
            }

            return null;
        }
    };

    /**
     * Default number of commits (or bug-fixes, whatever) that a commit window should contain
     */
    public abstract int defaultSize();

    /**
     * @param snapshot     The snapshot to validate
     * @param expectedSize Number of expected commits or bug-fixes, depending on the mode
     * @throws AssertionError if the snapshot does not contain the specified number of commits
     */
    public abstract void validateSnapshotSize(ISnapshot snapshot, final int expectedSize) throws AssertionError;

    public abstract int countRelevantCommits(Collection<Commit> commits);

    /**
     * Call {@link Iterator#next()} until n-many relevant commits have been seen, or until the iterator does not provide
     * any more elements.
     *
     * @param iter an Iterator
     * @param n    A non-negative number indicating the number of commits which should be skipped. If 0, the iterator
     *             will be advanced to the next bug-fix commit, if such a commit exists.
     * @return The n-th bug-fix commit. If no such relevant commit exists (because the iterator stops returning elements
     * before that), <code>null</code> is returned.
     */
    public abstract Commit skipNRelevantCommits(Iterator<Commit> iter, final int n);
}
