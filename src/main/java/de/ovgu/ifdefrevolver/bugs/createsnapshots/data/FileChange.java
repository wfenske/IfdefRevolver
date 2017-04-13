package de.ovgu.ifdefrevolver.bugs.createsnapshots.data;

import java.util.Date;

public class FileChange implements Comparable<FileChange> {

    private String filename;
    private Commit commit;

    /**
     * Instantiates a new changedFile.
     *
     * @param name the name of the changed file
     * @param the  commit that changed the file
     */
    public FileChange(String name, Commit commit) {
        this.filename = name;
        this.commit = commit;
    }

    public Date getDate() {
        return this.commit.getDate();
    }

    public String getHash() {
        return this.commit.getHash();
    }

    public String getFilename() {
        return this.filename;
    }

    @Override
    public int compareTo(FileChange obj) {
        if (obj.getDate().after(this.getDate()))
            return -1;
        else if (obj.getDate().before(this.getDate()))
            return 1;
        else
            return obj.getFilename().compareTo(this.getFilename());
    }

}
