package de.ovgu.ifdefrevolver.bugs.correlate.data;

import de.ovgu.ifdefrevolver.bugs.createsnapshots.data.ISnapshot;
import de.ovgu.ifdefrevolver.bugs.createsnapshots.input.FileFinder;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Represents a snapshot
 *
 * @author wfenske
 */
public class Snapshot implements Comparable<Snapshot>, IMinimalSnapshot, ISnapshot {
    private final int snapshotIndex;
    //    private final int branch;
    private final Date startDate;
    private final String startDateString;
    private final Set<Commit> commits;
    private final Commit startCommit;
    private final File snapshotDir;
    //private Set<String> bugfixCommits = new HashSet<>();

    public Snapshot(int snapshotIndex, String startDateString, Date startDate, Set<Commit> commits, File snapshotDir) {
        this.snapshotIndex = snapshotIndex;
        this.startDateString = startDateString;
        this.startDate = startDate;
        this.commits = commits;
        this.startCommit = commits.iterator().next();
        this.snapshotDir = snapshotDir;
    }

    @Override
    public String getStartDateString() {
        return startDateString;
    }

    /**
     * @return The (unique) sort index of this snapshot. Earlier snapshots have smaller sort indices than later
     * snapshots.
     */
    public int getIndex() {
        return snapshotIndex;
    }

//    public int getBranch() {
//        return branch;
//    }

    @Override
    public Date getStartDate() {
        return startDate;
    }

    @Override
    public Set<Commit> getCommits() {
        return commits;
    }

    @Override
    public Commit getStartCommit() {
        return startCommit;
    }

    /*
     * Mostly Eclipse-generated
     */
    @Override
    public String toString() {
        return String.format("Snapshot [index=%s, date=%s, startHash=%s]", snapshotIndex,
                getStartDateString(), startCommit);
    }

    /*
     * Eclipse-generated
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((startDate == null) ? 0 : startDate.hashCode());
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
        if (startDate == null) {
            if (other.startDate != null)
                return false;
        } else if (!startDate.equals(other.startDate))
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
        return this.getIndex() - other.getIndex();
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
