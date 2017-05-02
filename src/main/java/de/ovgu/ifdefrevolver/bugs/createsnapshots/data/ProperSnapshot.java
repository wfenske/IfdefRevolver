/**
 *
 */
package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import org.apache.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SortedSet;

/**
 * Represents a proper snapshot, which is a non-empty collection of
 * {@link Commit} instances, ordered in ascending order by date. A snapshot
 * comprises a non-empty number of bug-fix commits.
 *
 * @author wfenske
 */
public class ProperSnapshot implements ISnapshot {
    private static Logger log = Logger.getLogger(ProperSnapshot.class);

    private final SortedSet<Commit> commits;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
    private final int sortIndex;

    public ProperSnapshot(SortedSet<Commit> commits, int sortIndex) {
        if (commits == null) {
            throw new NullPointerException("Attempt to instantiate proper snapshot with null for commits.");
        }

        if (commits.isEmpty()) {
            throw new NullPointerException("Attempt to instantiate proper snapshot with an empty map of commits.");
        }

        this.commits = commits;
        this.sortIndex = sortIndex;
    }

    private Commit representativeCommit() {
        return commits.first();
    }

    @Override
    public String revisionHash() {
        Commit c = representativeCommit();
        return c.getHash();
    }

    @Override
    public Date revisionDate() {
        Commit c = representativeCommit();
        return c.getDate();
    }

    @Override
    public String revisionDateString() {
        Date d = revisionDate();
        synchronized (dateFormatter) {
            return dateFormatter.format(d);
        }
    }

    @Override
    public Date startDate() {
        return commits.first().getDate();
    }

    @Override
    public Date endDate() {
        return commits.last().getDate();
    }

    /**
     * @return A non-empty map of commits, sorted in ascending order.
     */
    @Override
    public SortedSet<Commit> getCommits() {
        return commits;
    }

    @Override
    public String toString() {
        final String fromDateString;
        final String toDateString;

        synchronized (dateFormatter) {
            fromDateString = dateFormatter.format(startDate());
            toDateString = dateFormatter.format(endDate());
        }

        return String.format("%s [index: %d, from=%s, to=%s]",
                this.getClass().getSimpleName(), sortIndex, fromDateString, toDateString);
    }

    public int getSortIndex() {
        return sortIndex;
    }
}
