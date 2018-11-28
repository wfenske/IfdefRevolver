package de.ovgu.ifdefrevolver.bugs.correlate.data;

import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.FileFinder;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Represents a snapshot
 *
 * @author wfenske
 */
public class Snapshot implements Comparable<Snapshot>, IMinimalSnapshot {
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    private final int snapshotIndex;
    private final int branch;
    private final Date snapshotDate;
    private final Set<Commit> commits;
    private final Commit startCommit;
    private final File snapshotDir;
    //private Set<String> bugfixCommits = new HashSet<>();

    public Snapshot(int snapshotIndex, int branch, Date snapshotDate, Set<Commit> commits, File snapshotDir) {
        this.snapshotIndex = snapshotIndex;
        this.branch = branch;
        this.snapshotDate = snapshotDate;
        this.commits = commits;
        this.startCommit = commits.iterator().next();
        this.snapshotDir = snapshotDir;
    }

    public synchronized String getFormattedSnapshotDate() {
        return dateFormatter.format(snapshotDate);
    }

    /**
     * @return The (unique) sort index of this snapshot. Earlier snapshots have smaller sort indices than later
     * snapshots.
     */
    public int getSnapshotIndex() {
        return snapshotIndex;
    }

    public int getBranch() {
        return branch;
    }

    @Override
    public Date getSnapshotDate() {
        return snapshotDate;
    }

    @Override
    public Set<Commit> getCommits() {
        return commits;
    }

    public Commit getStartCommit() {
        return startCommit;
    }

    /*
     * Mostly Eclipse-generated
     */
    @Override
    public String toString() {
        return String.format("Snapshot [index=%s, date=%s, startHash=%s]", snapshotIndex,
                getFormattedSnapshotDate(), startCommit);
    }

    /*
     * Eclipse-generated
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((snapshotDate == null) ? 0 : snapshotDate.hashCode());
        result = prime * result + snapshotIndex;
        result = prime * result + ((startCommit == null) ? 0 : startCommit.hashCode());
        return result;
    }

    /*
     * Eclipse-generated
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Snapshot))
            return false;
        Snapshot other = (Snapshot) obj;
        if (snapshotDate == null) {
            if (other.snapshotDate != null)
                return false;
        } else if (!snapshotDate.equals(other.snapshotDate))
            return false;
        if (snapshotIndex != other.snapshotIndex)
            return false;
        if (startCommit == null) {
            if (other.startCommit != null)
                return false;
        } else if (!startCommit.equals(other.startCommit))
            return false;
        return true;
    }

    @Override
    public int compareTo(Snapshot other) {
        return this.getSnapshotIndex() - other.getSnapshotIndex();
    }

    /**
     * @return All SrcML files of the C files within the snapshot
     */
    public List<File> listSrcmlCFiles() {
        File srcmlFolder = new File(snapshotDir, "_cppstats");
        return FileFinder.find(srcmlFolder, ".*\\.c\\.xml$");
    }

//    public void addBugfixCommit(String commitId) {
//        this.bugfixCommits.add(commitId);
//    }
//
//    @Override
//    public boolean isBugfixCommit(String commitId) {
//        return this.bugfixCommits.contains(commitId);
//    }
}
