package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;

import java.util.Comparator;
import java.util.Optional;

/**
 * A subset of the information in a row of the <code>function_change_hunks.csv</code> file
 */
public class FunctionChangeRow {
    public static final Comparator<? super FunctionChangeRow> BY_HUNK_AND_MOD_TYPE = new Comparator<FunctionChangeRow>() {
        @Override
        public int compare(FunctionChangeRow o1, FunctionChangeRow o2) {
            int r;
            //r = FunctionId.BY_FILE_AND_SIGNATURE_ID.compare(o1.functionId, o2.functionId);
            //if (r != 0) return r;
            r = o1.hunk - o2.hunk;
            if (r != 0) return r;
            r = o1.modType.ordinal() - o2.modType.ordinal();
            return r;
        }
    };

    public FunctionId functionId;
    public Optional<Commit> previousRevision;
    public Commit commit;
    public int hunk;
    public int linesAdded;
    public int linesDeleted;
    public FunctionChangeHunk.ModificationType modType;
    public Optional<FunctionId> newFunctionId;

    @Override
    public String toString() {
        return "FunctionChangeRow{" +
                "modType=" + modType +
                ", " + previousRevisionToString() + commit.commitHash +
                ", functionId=" + functionId +
                ", newFunctionId=" + (newFunctionId.isPresent() ? newFunctionId : "") +
                '}';
    }

    private String previousRevisionToString() {
        if (previousRevision.isPresent()) {
            return previousRevision.get().commitHash + "..";
        } else {
            return "";
        }
    }

    private boolean isMove() {
        return this.modType == FunctionChangeHunk.ModificationType.MOVE;
    }

    public boolean isMoveOfIdenticalFunctionIds(FunctionChangeRow other) {
        if (!this.isMove() || !other.isMove()) return false;
        if (!functionId.equals(other.functionId)) return false;
        if (!newFunctionId.get().equals(other.newFunctionId.get())) return false;
        return true;
    }

    public boolean isSamePreviousRevisionAndCommit(FunctionChangeRow other) {
        if (this.commit != other.commit) return false;
        if (this.previousRevision.isPresent() && other.previousRevision.isPresent()) {
            return this.previousRevision.get() == other.previousRevision.get();
        } else {
            return this.previousRevision.isPresent() == other.previousRevision.isPresent();
        }
    }
}
