package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import java.util.Date;
import java.util.SortedSet;

/**
 * Represents a snapshot, which is a collection of {@link Commit} instances,
 * ordered in ascending order by date.
 *
 * @author wfenske
 */
public interface ISnapshot {

    /**
     * @return The hash of the git commit that should be checked out to get the source code of this snapshot. If this is
     * the {@link NullSnapshot} , <code>null</code> is returned, otherwise, the return value is always
     * non-<code>null</code>.
     */
    String revisionHash();

    /**
     * @return Nullable date of this snapshot. If this is the {@link NullSnapshot}, <code>null</code> is returned,
     * otherwise, the return value is always non-<code>null</code>.
     */
    Date revisionDate();

    /**
     * @return Nullable formatted date of this snapshot. If this the {@link NullSnapshot}, <code>null</code> is
     * returned, otherwise, the return value is always non-<code>null</code>.
     */
    String revisionDateString();

    /**
     * @return Nullable date of the first commit of this snapshot. If this is the {@link NullSnapshot},
     * <code>null</code> is returned, otherwise, the return value is always non-<code>null</code>.
     */
    Date startDate();

    /**
     * @return Nullable date of the last commit of this snapshot. If this is the {@link NullSnapshot}, <code>null</code>
     * is returned, otherwise, the return value is always non-<code>null</code>.
     */
    Date endDate();

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
    SortedSet<Commit> getCommits();
}
