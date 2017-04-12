package de.ovgu.skunk.bugs.createsnapshots.main;

import de.ovgu.skunk.bugs.createsnapshots.data.ISnapshot;
import de.ovgu.skunk.bugs.createsnapshots.data.ProperSnapshot;

import java.util.Collection;

/**
 * Created by wfenske on 06.04.17.
 */
interface ISnapshotProcessingModeStrategy {
    /**
     * Delete output files, such as the projectInfo.csv, if they are
     * recreated anyway
     */
    void removeOutputFiles();

    /**
     * Create the Snapshot, if it does not already exist. Snapshots are
     * stored on disk in the folder {@link CreateSnapshotsConfig#projectSnapshotsDir()}
     * /&lt;date&gt;, where &lt;date&gt; hsa the format
     * &quot;YYYY-MM-DD&quot;.
     *
     * @param currentSnapshot Start date of the current snapshot
     */
    void ensureSnapshot(ProperSnapshot currentSnapshot);

    /**
     * Run cppstats (if necessary) and Skunk
     *
     * @param currentSnapshot The current snapshot
     */
    void processSnapshot(ProperSnapshot currentSnapshot);

    /**
     * @return String describing in human-readable form what this strategy is about to do.
     */
    String activityDisplayName();

    boolean isCurrentSnapshotDependentOnPreviousSnapshot();

    void setPreviousSnapshot(ISnapshot previousSnapshot);

    void readAllRevisionsAndComputeSnapshots();

    Collection<ProperSnapshot> getSnapshotsToProcess();

    /**
     * Check whether the given snapshot has already been processed.  If so, processing this snapshot can be skipped.
     *
     * @param snapshot The snapshot to test
     * @return <code>true</code> if processing this snapshot can safely be skipped. <code>false</code> otherwise.
     */
    boolean snapshotAlreadyProcessed(ProperSnapshot snapshot);
}
