package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;

import java.util.Optional;

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
     * Function after the change (may have a different signature or reside in a new file)
     */
    private Optional<Method> newFunction;

    public static enum ModificationType {
        /**
         * if this change adds the entire function
         */
        ADD,
        /**
         * if this change moved a function to another place
         */
        MOVE,
        /**
         * if this change neither adds nor deletes the entire function but simply modifies parts of it
         */
        MOD,
        /**
         * if this change deletes the entire function (happens sometimes if a function is moved to another file or
         * within the same file)
         */
        DEL
    }

    private ModificationType modType;

    public FunctionChangeHunk(Method function, ChangeHunk hunk, ModificationType modType) {
        this.function = function;
        this.hunk = hunk;
        this.modType = modType;
        this.newFunction = Optional.empty();
    }

    public FunctionChangeHunk(Method function, ChangeHunk hunk, ModificationType modType, Method newFunction) {
        this.function = function;
        this.hunk = hunk;
        this.modType = modType;
        this.newFunction = Optional.of(newFunction);
    }

    public static FunctionChangeHunk makePseudoAdd(String commitId, String oldPath, String newPath, Method func) {
        ChangeHunk ch = new ChangeHunk(commitId, oldPath, newPath, -1, 0, func.getSignatureGrossLinesOfCode());
        FunctionChangeHunk fh = new FunctionChangeHunk(func, ch, FunctionChangeHunk.ModificationType.ADD);
        return fh;
    }

    public static FunctionChangeHunk makePseudoDel(String commitId, String oldPath, String newPath, Method func) {
        ChangeHunk ch = new ChangeHunk(commitId, oldPath, newPath, -1, func.getSignatureGrossLinesOfCode(), 0);
        FunctionChangeHunk fh = new FunctionChangeHunk(func, ch, FunctionChangeHunk.ModificationType.DEL);
        return fh;
    }

    public Method getFunction() {
        return function;
    }

    public Optional<Method> getNewFunction() {
        return newFunction;
    }

    public ChangeHunk getHunk() {
        return hunk;
    }

    public ModificationType getModType() {
        return modType;
    }

    @Override
    public String toString() {
        final String newFuncText;
        if (newFunction.isPresent()) {
            newFuncText = " -> " + newFunction.get();
        } else {
            newFuncText = "";
        }
        return this.getClass().getSimpleName() + "{" +
                "f=" + function + newFuncText +
                ", hunk=" + hunk +
                ", modificationType=" + modType +
                '}';
    }
}
