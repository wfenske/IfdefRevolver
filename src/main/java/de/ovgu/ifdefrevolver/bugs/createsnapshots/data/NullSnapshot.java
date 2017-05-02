/**
 *
 */
package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import java.util.Collections;
import java.util.Date;
import java.util.SortedSet;

/**
 * @author wfenske
 */
public class NullSnapshot implements ISnapshot {

    private static final NullSnapshot INSTANCE = new NullSnapshot();

    /**
     * @return An instance of the {@link NullSnapshot}
     */
    public static final NullSnapshot getInstance() {
        return INSTANCE;
    }

    private NullSnapshot() {
        // Not meant to be publically instanciated
    }

    @Override
    public Date revisionDate() {
        return null;
    }

    @Override
    public String revisionDateString() {
        return null;
    }

    @Override
    public SortedSet<Commit> getCommits() {
        return Collections.emptySortedSet();
    }

    @Override
    public Date startDate() {
        return null;
    }

    @Override
    public Date endDate() {
        return null;
    }

    @Override
    public String revisionHash() {
        return null;
    }

    @Override
    public String toString() {
        return "NullSnapshot";
    }

}
