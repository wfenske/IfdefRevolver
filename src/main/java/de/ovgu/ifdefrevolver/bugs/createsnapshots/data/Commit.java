/**
 *
 */
package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import java.util.Date;

/**
 * @author wfenske
 */
public class Commit implements Comparable<Commit> {
    private final String hash;
    private final Date date;
    private final boolean bugfix;
    private final int branch;
    private final int positionInBranch;

    public Commit(int branch, int positionInBranch, String hash, Date date, boolean bugfix) {
        this.branch = branch;
        this.positionInBranch = positionInBranch;
        this.hash = hash;
        this.date = date;
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
    public Date getDate() {
        return date;
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
        if (!(obj instanceof Commit))
            return false;
        Commit other = (Commit) obj;
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
    public int compareTo(Commit o) {
        int cmp = this.branch - o.branch;
        if (cmp != 0)
            return cmp;
        cmp = this.positionInBranch - o.positionInBranch;
        return cmp;
    }
}
