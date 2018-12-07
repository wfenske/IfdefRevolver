/**
 *
 */
package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;

import java.util.Collections;
import java.util.Date;
import java.util.Set;

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
    public String getStartDateString() {
        return null;
    }

    @Override
    public Set<Commit> getCommits() {
        return Collections.emptySet();
    }

    @Override
    public Date getStartDate() {
        return null;
    }

    @Override
    public Commit getStartCommit() {
        return null;
    }

    @Override
    public String toString() {
        return "NullSnapshot";
    }

}
