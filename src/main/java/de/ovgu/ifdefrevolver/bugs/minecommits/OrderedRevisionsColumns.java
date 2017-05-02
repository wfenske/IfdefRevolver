package de.ovgu.ifdefrevolver.bugs.minecommits;

import org.apache.log4j.Logger;

/**
 * Created by wfenske on 10.03.17.
 */
public enum OrderedRevisionsColumns {
    /**
     * Number of the (logical) branch this commit is assigned to. This probably differs from what GIT calls a branch,
     * but it represents a series of commits where one commit is always the parent of the next commit.
     */
    BRANCH {
        @Override
        public String stringValue(OrderedCommit c) {
            return c.getBranchNumber() + "";
        }
    },
    /**
     * Number of the commit within it's branch. The root gets 1, its child gets 2, and so on.
     */
    POSITION {
        @Override
        public String stringValue(OrderedCommit c) {
            return c.getBranchPosition() + "";
        }
    },
    COMMIT_ID {
        @Override
        public String stringValue(OrderedCommit c) {
            return c.getHash();
        }
    }, TIMESTAMP {
        @Override
        public String stringValue(OrderedCommit c) {
            return c.getFormattedTimestamp();
        }
    };

    private static Logger LOG = Logger.getLogger(OrderedRevisionsColumns.class);

    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public abstract String stringValue(OrderedCommit c);

    public String commitToString(OrderedCommit c) {
        if (c.isMerge()) {
            LOG.info("Ignoring commit " + c.getHash() + ": merge commit.");
        }
        StringBuilder line = new StringBuilder();
        for (OrderedRevisionsColumns columns : OrderedRevisionsColumns.values()) {
            if (line.length() != 0) {
                line.append(',');
            }
            line.append(columns.stringValue(c));
        }
        String s = line.toString();
        System.out.println(s);
        return s;
    }
}
