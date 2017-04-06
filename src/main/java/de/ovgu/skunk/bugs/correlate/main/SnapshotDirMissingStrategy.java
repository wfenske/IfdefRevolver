package de.ovgu.skunk.bugs.correlate.main;

import java.io.File;

/**
 * Created by wfenske on 06.04.17.
 */
public enum SnapshotDirMissingStrategy {
    THROW_IF_MISSING {
        @Override
        public void handleMissingSnapshotDirectory(File projectSnapshotsDir) {
            throw new RuntimeException(
                    "The project's snapshots directory does not exist or is not a directory: "
                            + projectSnapshotsDir.getAbsolutePath());
        }
    },
    CREATE_IF_MISSING {
        @Override
        public void handleMissingSnapshotDirectory(File projectSnapshotsDir) {
            if (!projectSnapshotsDir.mkdirs()) {
                throw new RuntimeException(
                        "Failed to create the project's snapshot directory at: "
                                + projectSnapshotsDir.getAbsolutePath());
            }
        }
    };

    public abstract void handleMissingSnapshotDirectory(File projectSnapshotsDir);
}
