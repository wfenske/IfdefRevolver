package de.ovgu.ifdefrevolver.commitanalysis;

import java.util.Optional;

/**
 * A subset of the information in a row of the <code>function_change_hunks.csv</code> file
 */
public class FunctionChangeRow {
    public FunctionId functionId;
    public String commitId;
    public int linesAdded;
    public int linesDeleted;
    public FunctionChangeHunk.ModificationType modType;
    public Optional<FunctionId> newFunctionId;

    @Override
    public String toString() {
        return "FunctionChangeRow{" +
                "modType=" + modType +
                ", commitId='" + commitId + '\'' +
                ", functionId=" + functionId +
                ", newFunctionId=" + (newFunctionId.isPresent() ? newFunctionId : "") +
                '}';
    }
}
