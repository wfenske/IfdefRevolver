/**
 * 
 */
package de.ovgu.skunk.bugs.concept.data;

import java.util.Date;

/**
 * @author wfenske
 */
public class Commit implements Comparable<Commit> {
    private final String hash;
    private final Date date;
    private final boolean bugfix;

    public Commit(String hash, Date date, boolean bugfix) {
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
     * @return <code>true</code> if this is a bugfix commit, <code>false</code>
     *         otherwise
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

    @Override
    public int compareTo(Commit o) {
        int cmp = this.getDate().compareTo(o.getDate());
        if (cmp != 0)
            return cmp;
        return this.getHash().compareTo(o.getHash());
    }
}
