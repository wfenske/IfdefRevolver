package de.ovgu.ifdefrevolver.bugs.correlate.main;

import de.ovgu.ifdefrevolver.commitanalysis.branchtraversal.SnapshotsColumns;

import java.io.File;
import java.util.Date;

/**
 * Created by wfenske on 09.02.17.
 */
public interface IHasResultsDir {
    File projectResultsDir();

    void setResultsDir(String resultsDir);

    /**
     * Directory in which computed information related to the snapshot with the given date resides.
     */
    File snapshotResultsDirForDate(Date date);

    /**
     * Directory in which computed information related to the snapshot with the given commit hash resides.
     */
    File snapshotResultsDirForCommit(String commitHash);

    default File snapshotsCsvFile() {
        return new File(projectResultsDir(), SnapshotsColumns.FILE_BASENAME);
    }
}
