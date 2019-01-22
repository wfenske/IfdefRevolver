package de.ovgu.ifdefrevolver.bugs.correlate.main;

import java.io.File;
import java.util.Date;

/**
 * Created by wfenske on 09.02.17.
 */
public interface IHasSnapshotsDir {
    File projectSnapshotsDir();

    //void setSnapshotsDir(String snapshotsDir);

    File snapshotDirForDate(Date date);
}
