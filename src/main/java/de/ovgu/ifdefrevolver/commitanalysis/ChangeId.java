package de.ovgu.ifdefrevolver.commitanalysis;

public class ChangeId {
    /**
     * ID of the revision before this commit.
     */
    public final String previousRevisionId;
    /**
     * IDs of the commits to which this hunk belongs
     */
    public final String commitId;

    public ChangeId(String previousRevisionId, String commitId) {
        this.previousRevisionId = previousRevisionId;
        this.commitId = commitId;
    }

    @Override
    public String toString() {
        return "ChangeId{" +
                previousRevisionId +
                ".." + commitId +
                '}';
    }
}
