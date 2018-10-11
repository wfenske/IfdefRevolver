package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;

import java.util.Objects;

public class FunctionIdWithCommit {
    public final FunctionId functionId;
    public final CommitsDistanceDb.Commit commit;
    public final boolean move;

    public FunctionIdWithCommit(FunctionId functionId, CommitsDistanceDb.Commit commit, boolean move) {
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FunctionIdWithCommit{");
        sb.append("functionId=").append(functionId);
        sb.append(", commit='").append(commit).append('\'');
        sb.append(", move=").append(move);
        sb.append('}');
        return sb.toString();
    }
}
