package de.ovgu.skunk.bugs.createsnapshots.data;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.SortedMap;

/**
 * Represents a snapshot, which is a collection of {@link Commit} instances,
 * ordered in ascending order by date.
 *
 * @author wfenske
 */
public interface ISnapshot {

    /**
     * @return The hash of the git commit that should be checked out to get the
     * source code of this snapshot. If this is the {@link NullSnapshot}
     * , <code>null</code> is returned, otherwise, the return value is
     * always non-<code>null</code>.
     */
    String revisionHash();

    /**
     * @return Nullable date of this snapshot. If this is the
     * {@link NullSnapshot}, <code>null</code> is returned, otherwise,
     * the return value is always non-<code>null</code>.
     */
    Date revisionDate();

    /**
     * @return Nullable formatted date of this snapshot. If this the
     * {@link NullSnapshot}, <code>null</code> is returned, otherwise,
     * the return value is always non-<code>null</code>.
     */
    String revisionDateString();

    /**
     * @return Nullable date of the first commit of this snapshot. If this is
     * the {@link NullSnapshot}, <code>null</code> is returned,
     * otherwise, the return value is always non-<code>null</code>.
     */
    Date startDate();

    /**
     * @return Nullable date of the last commit of this snapshot. If this is the
     * {@link NullSnapshot}, <code>null</code> is returned, otherwise,
     * the return value is always non-<code>null</code>.
     */
    Date endDate();

    /**
     * <p>
     * Returns the map of commits of this snapshot, in ascending order by date.
     * </p>
     * <p>
     * <em>Warning:</em> The results are undefined if this map is modified.
     * </p>
     *
     * @return The map of commits contained in this snapshot. This map is always
     * non-<code>null</code>. Unless this is the {@link NullSnapshot},
     * this map is also non-empty.
     */
    SortedMap<Commit, Set<FileChange>> getCommits();

    /**
     * Compute the collection of files in the next snapshot which have changed
     * since the checkout associated with the snapshot on which this method is
     * called. The returned collection may be an overapproxomation, i.e., files
     * may be included although they have not changed.
     *
     * @param filesInNextSnapshotCheckout All the files that belong to the checkout of the following
     *                                    snapshot (second parameter)
     * @param nextSnapshot                The snapshot that the files (first argument) belong to
     * @return A collection of those files that have changed.
     */
    Collection<File> computeChangedFiles(Collection<File> filesInNextSnapshotCheckout, ISnapshot nextSnapshot);
}
