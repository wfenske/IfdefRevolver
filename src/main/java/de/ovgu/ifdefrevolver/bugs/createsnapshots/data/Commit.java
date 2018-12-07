/**
 *
 */
package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import de.ovgu.ifdefrevolver.util.DateUtils;

import java.util.Date;

/**
 * @author wfenske
 */
class _Commit implements Comparable<_Commit> {
    private final String hash;
    private Date timestamp;
    private final boolean bugfix;
    private final int branch;
    private final int positionInBranch;

    public _Commit(int branch, int positionInBranch, String hash, Date timestamp, boolean bugfix) {
        this.branch = branch;
        this.positionInBranch = positionInBranch;
        this.hash = hash;
        this.timestamp = timestamp;
        this.bugfix = bugfix;
    }

    /**
     * @return the commit's hash
     */
    public String getHash() {
        return hash;
    }

    /**
     * @return the date of the commit
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * @return <code>true</code> if this is a bugfix commit, <code>false</code> otherwise
     */
    public boolean isBugfix() {
        return bugfix;
    }

    @Override
    public int hashCode() {
        return ((hash == null) ? 0 : hash.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof _Commit))
            return false;
        _Commit other = (_Commit) obj;
        if (hash == null) {
            if (other.hash != null)
                return false;
        } else if (!hash.equals(other.hash))
            return false;
        return true;
    }

    public int getBranch() {
        return branch;
    }

    @Override
    public int compareTo(_Commit o) {
        int cmp = this.branch - o.branch;
        if (cmp != 0)
            return cmp;
        cmp = this.positionInBranch - o.positionInBranch;
        return cmp;
    }

    public boolean isAtLeastOneDayBefore(_Commit other) {
        return DateUtils.isAtLeastOneDayBefore(this.getTimestamp(), other.getTimestamp());
    }

    public void advanceTimestampOneDay() {
        timestamp = org.apache.commons.lang3.time.DateUtils.addDays(timestamp, 1);
    }

    public void setDate(Date date) {
        this.timestamp = date;
    }
}
