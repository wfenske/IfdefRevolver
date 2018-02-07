package de.ovgu.ifdefrevolver.bugs.minecommits;

/**
 * Describes the structure of a CSV file that represents the parent-child relationship of the commits in a repository
 * <p>
 * Created by wfenske on 2018-02-07
 */
public enum CommitParentsColumns {
    /**
     * GIT hash of the commit. A commit with mutliple parents will occur multiple times. A commit without a (parsable)
     * parent will be listed just once.
     */
    COMMIT,
    /**
     * Timestamp, according to the GIT repository
     */
    TIMESTAMP,
    /**
     * Hash of the parent commit; will be the empty string if there is no parent or if we could not parse the parent
     * commit
     */
    PARENT;

    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
}
