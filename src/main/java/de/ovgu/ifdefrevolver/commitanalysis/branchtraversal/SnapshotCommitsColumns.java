package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;

public enum SnapshotCommitsColumns implements CsvColumnValueProvider<Commit, Snapshot> {
    SNAPSHOT_INDEX {
        @Override
        public Integer csvColumnValue(Commit ignored, Snapshot s) {
            return s.getIndex();
        }
    },
    COMMIT_HASH {
        @Override
        public String csvColumnValue(Commit commit, Snapshot s) {
            return commit.commitHash;
        }
    };

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project- and snapshot-specific directory, such as <code>results/busybox/1999-10-05/</code>
     */
    public static final String FILE_BASENAME = "snapshot_commits.csv";

}
