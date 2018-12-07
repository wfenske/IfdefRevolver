package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;

import java.util.Date;
import java.util.Set;

/**
 * Represents a snapshot, which is a collection of {@link Commit} instances, ordered in ascending order by date.
 *
 * @author wfenske
 */
public interface ISnapshot {

    /**
     * @return The hash of the git commit that should be checked out to get the source code of this snapshot. If this is
     * the {@link NullSnapshot} , <code>null</code> is returned, otherwise, the return value is always
     * non-<code>null</code>.
     */
    Commit getStartCommit();

    /**
     * @return Nullable formatted date of this snapshot. If this the {@link NullSnapshot}, <code>null</code> is
     * returned, otherwise, the return value is always non-<code>null</code>.
     */
    String getStartDateString();

    /**
     * @return Nullable date of the first commit of this snapshot. If this is the {@link NullSnapshot},
     * <code>null</code> is returned, otherwise, the return value is always non-<code>null</code>.
     */
    Date getStartDate();

    /**
     * <p>
     * Returns the set of commits of this snapshot, in ascending order by date.
     * </p>
     * <p>
     * <em>Warning:</em> The results are undefined if this map is modified.
     * </p>
     *
     * @return The set of commits contained in this snapshot. This map is always non-<code>null</code>. Unless this is
     * the {@link NullSnapshot}, this map is also non-empty.
     */
    Set<Commit> getCommits();
}
