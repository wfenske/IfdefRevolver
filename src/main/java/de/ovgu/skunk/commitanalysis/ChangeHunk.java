package de.ovgu.skunk.commitanalysis;

/**
 * Models a hunk of a patch/commit within a file
 * <p>
 * Created by wfenske on 02.03.17.
 */
public class ChangeHunk {
    /**
     * ID of the commit to which this hunk belongs
     */
    String commitId;
    /**
     * File being changed (old file name, in contrast to the new file name in cases where a file is renamed or newly created)
     */
    String aSidePath;
    /**
     * Number of the change hunk within the file {@link #aSidePath}, counted from 0
     */
    int hunkNo;
    /**
     * Lines deleted by this hunk
     */
    int linesDeleted;
    /**
     * Lines added by this hunk
     */
    int linesAdded;
}
