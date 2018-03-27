package de.ovgu.ifdefrevolver.bugs.correlate.data;

import java.util.Date;
import java.util.Set;

public interface IMinimalSnapshot {
    /**
     * @return The (start) date of this snapshot
     */
    Date getSnapshotDate();

    /**
     * @return Hashes of the commits within this snapshot. The iterator of this
     * set returns the hashes in chronological order.
     */
    Set<String> getCommitHashes();

    boolean isBugfixCommit(String commitHash);
}
