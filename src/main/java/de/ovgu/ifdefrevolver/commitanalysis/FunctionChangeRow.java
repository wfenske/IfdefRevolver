package de.ovgu.ifdefrevolver.commitanalysis;

import java.util.Optional;

public class FunctionChangeRow {
    public FunctionId functionId;
    public String commitId;
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
