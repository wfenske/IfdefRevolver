package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;

/**
 * Information about a hunk of change within a commit and the function that it changes
 * <p>
 * Created by wfenske on 02.03.17.
 */
public class FunctionChangeHunk {
    /**
     * The function being changed
     */
    private Method function;
    /**
     * The change applied to the function
     */
    private ChangeHunk hunk;

    /**
     * {@code true} if this change deletes the entire function (happens
     * sometimes if a function is moved to another file or within the
     * same file)
     */
    private boolean deletesFunction;

    public FunctionChangeHunk(Method function, ChangeHunk hunk, boolean deletesFunction) {
        this.function = function;
        this.hunk = hunk;
        this.deletesFunction = deletesFunction;
    }

    public Method getFunction() {
        return function;
    }

    public ChangeHunk getHunk() {
        return hunk;
    }


    /**
     * @return {@code true} if this change deletes the entire function (happens
     * sometimes if a function is moved to another file or within the
     * same file)
     */
    public boolean deletesFunction() {
        return deletesFunction;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "f=" + function +
                ", hunk=" + hunk +
                ", deletesFunction=" + deletesFunction +
                '}';
    }
}
