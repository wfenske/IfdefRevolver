package de.ovgu.ifdefrevolver.bugs.correlate.input;

import java.io.File;
import java.util.Date;
import java.util.Set;

/**
 * Created by wfenske on 06.04.17.
 */
public class RawSnapshotInfo {
    public final int sortIndex;
    public final Date date;
    public final Set<String> commitHashes;
    public final File snapshotDir;

    public RawSnapshotInfo(int sortIndex, Date date, Set<String> commitHashes, File snapshotDir) {
        this.sortIndex = sortIndex;
        this.date = date;
        this.commitHashes = commitHashes;
        this.snapshotDir = snapshotDir;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "sortIndex=" + sortIndex +
                ", date=" + date +
                ", commitHashes=" + commitHashes +
                ", snapshotDir=" + snapshotDir +
                '}';
    }
}
