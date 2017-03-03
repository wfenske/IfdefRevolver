package de.ovgu.skunk.commitanalysis;

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

    public FunctionChangeHunk(Method function, ChangeHunk hunk) {
        this.function = function;
        this.hunk = hunk;
    }

    public Method getFunction() {
        return function;
    }

    public ChangeHunk getHunk() {
        return hunk;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "f=" + function +
                ", hunk=" + hunk +
                '}';
    }
}
