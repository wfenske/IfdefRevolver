package de.ovgu.skunk.bugs.correlate.main;

import java.io.File;

/**
 * Created by wfenske on 09.02.17.
 */
public interface IHasProjectSnapshotsDir {
    File projectSnapshotsDir();

    void setSnapshotsDir(String snapshotsDir);
}
