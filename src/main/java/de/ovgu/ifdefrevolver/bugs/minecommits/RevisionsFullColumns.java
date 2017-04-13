package de.ovgu.ifdefrevolver.bugs.minecommits;

/**
 * Created by wfenske on 10.03.17.
 */
public enum RevisionsFullColumns {
    COMMIT_ID, FIX, FIX_KEYWORDS, FILE, COMMIT_TYPE, LINE_START, LINE_END, TIMESTAMP;
    //, IN_MAIN_BRANCH, BRANCHES
    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";
}
