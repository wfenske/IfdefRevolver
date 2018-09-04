package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;

import java.util.Objects;

public class FunctionIdWithCommit {
    public final FunctionId functionId;
    public final String commit;
    public final boolean move;

    public FunctionIdWithCommit(FunctionId functionId, String commit, boolean move) {
        this.functionId = functionId;
        this.commit = commit;
        this.move = move;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FunctionIdWithCommit)) return false;
        FunctionIdWithCommit that = (FunctionIdWithCommit) o;
        return Objects.equals(functionId, that.functionId) &&
                Objects.equals(commit, that.commit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionId, commit);
    }
}
