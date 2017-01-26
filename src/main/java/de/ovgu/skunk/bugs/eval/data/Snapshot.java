package de.ovgu.skunk.bugs.eval.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

/**
 * Represents a snapshot
 * 
 * @author wfenske
 */
public class Snapshot implements Comparable<Snapshot> {
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    private final int snapshotIndex;
    private final Date snapshotDate;
    private final Set<String> commitHashes;
    private final String startHash;

    public Snapshot(int snapshotIndex, Date snapshotDate, Set<String> commitHashes) {
        this.snapshotIndex = snapshotIndex;
        this.snapshotDate = snapshotDate;
        this.commitHashes = commitHashes;
        this.startHash = commitHashes.iterator().next();
    }

    /**
     * @return The (unique) sort index of this snapshot. Earlier snapshots have
     *         smaller sort indices than later snapshots.
     */
    public int getSnapshotIndex() {
        return snapshotIndex;
    }

    /**
     * @return The (start) date of this snapshot
     */
    public Date getSnapshotDate() {
        return snapshotDate;
    }

    /**
     * @return Hashes of the commits within this snapshot. The iterator of this
     *         set returns the hashes in chronological order.
     */
    public Set<String> getCommitHashes() {
        return commitHashes;
    }

    /*
     * Mostly Eclipse-generated
     */
    @Override
    public String toString() {
        return String.format("Snapshot [index=%s, date=%s, startHash=%s]", snapshotIndex,
                dateFormatter.format(snapshotDate), startHash);
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
        result = prime * result + ((startHash == null) ? 0 : startHash.hashCode());
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
        if (startHash == null) {
            if (other.startHash != null)
                return false;
        } else if (!startHash.equals(other.startHash))
            return false;
        return true;
    }

    @Override
    public int compareTo(Snapshot other) {
        return this.getSnapshotIndex() - other.getSnapshotIndex();
    }

}
