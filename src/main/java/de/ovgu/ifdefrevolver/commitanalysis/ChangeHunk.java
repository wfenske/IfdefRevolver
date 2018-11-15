package de.ovgu.ifdefrevolver.commitanalysis;

/**
 * Models a hunk of a patch/commit within a file
 * <p>
 * Created by wfenske on 02.03.17.
 */
public class ChangeHunk {
    /**
     * IDs of the commits to which this hunk belongs
     */
    private ChangeId changeId;

    /**
     * File being changed (old file name, in contrast to the new file name in cases where a file is renamed or newly
     * created)
     */
    private String oldPath;
    /**
     * File being changed (new file name)
     */
    private String newPath;
    /**
     * Number of the change hunk within the file {@link #oldPath}, counted from 0
     */
    private int hunkNo;
    /**
     * Lines deleted by this hunk
     */
    private int linesDeleted;
    /**
     * Lines added by this hunk
     */
    private int linesAdded;

    public ChangeHunk(ChangeId commitId, String oldPath, String newPath, int hunkNo, int linesDeleted, int linesAdded) {
        this.changeId = commitId;
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.hunkNo = hunkNo;
        this.linesDeleted = linesDeleted;
        this.linesAdded = linesAdded;
    }

    private ChangeHunk(ChangeHunk template) {
        this.changeId = template.changeId;
        this.oldPath = template.oldPath;
        this.newPath = template.newPath;
        this.hunkNo = template.hunkNo;
    }

    public ChangeHunk(ChangeHunk template, int linesDeleted, int linesAdded) {
        this(template);
        this.linesDeleted = linesDeleted;
        this.linesAdded = linesAdded;
    }

    public ChangeId getChangeId() {
        return changeId;
    }

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public int getHunkNo() {
        return hunkNo;
    }

    public int getLinesDeleted() {
        return linesDeleted;
    }

    public int getLinesAdded() {
        return linesAdded;
    }

    /**
     * @return Tally of lines deleted and added by the hunk.  Will be positive if more lines were added than deleted
     */
    public int getDelta() {
        return linesAdded - linesDeleted;
    }

    @Override
    public String toString() {
        final String path;
        if (oldPath.equals(newPath)) {
            path = oldPath;
        } else {
            path = oldPath + " -> " + newPath;
        }
        return this.getClass().getSimpleName() + '{' +
                "" + changeId.previousRevisionId +
                ".." + changeId.commitId +
                ", " + path +
                ", hunkNo=" + hunkNo +
                ", -" + linesDeleted +
                ", +" + linesAdded +
                ", delta: " + getDelta() +
                '}';
    }
}
