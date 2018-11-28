package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.skunk.detection.output.CsvColumnValueProvider;

public enum SnapshotsColumns implements CsvColumnValueProvider<Snapshot, Void> {
    SNAPSHOT_INDEX {
        @Override
        public Integer csvColumnValue(Snapshot s, Void ctx) {
            return s.getSnapshotIndex();
        }
    },
    SNAPSHOT_DATE {
        @Override
        public String csvColumnValue(Snapshot s, Void ctx) {
            return s.getFormattedSnapshotDate();
        }
    },
    START_COMMIT_HASH {
        @Override
        public String csvColumnValue(Snapshot s, Void ctx) {
            return s.getStartCommit().commitHash;
        }
    };

    /**
     * Basename of the CSV file that will hold this information.  It will be located within the results directory, in a
     * project-specific directory, such as <code>results/busybox/</code>
     */
    public static final String FILE_BASENAME = "snapshots.csv";

}
