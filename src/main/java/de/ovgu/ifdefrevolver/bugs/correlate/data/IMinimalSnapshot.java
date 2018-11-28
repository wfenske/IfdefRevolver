package de.ovgu.ifdefrevolver.bugs.correlate.data;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;

import java.util.Set;

public interface IMinimalSnapshot extends IHasSnapshotDate {

    /**
     * @return Hashes of the commits within this snapshot. The iterator of this set returns the hashes in chronological
     * order.
     */
    Set<Commit> getCommits();

    //boolean isBugfixCommit(String commitHash);
}
