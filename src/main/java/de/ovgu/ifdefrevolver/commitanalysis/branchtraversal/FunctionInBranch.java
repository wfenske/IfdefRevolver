package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;


import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.AbResRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.skunk.util.LinkedGroupingListMap;
import org.apache.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

class FunctionInBranch {
    private static Logger LOG = Logger.getLogger(FunctionInBranch.class);
    private final FunctionInBranchFactory factory;
    int uid;
    public final FunctionId firstId;
    private Map<Commit, AbResRow> jointFunctionAbSmellRows = null;
    private LinkedGroupingListMap<Commit, FunctionChangeRow> changes = null;
    private final boolean isLogDebug;

    protected FunctionInBranch(FunctionId firstId, int uid, FunctionInBranchFactory factory) {
        this.firstId = firstId;
        this.uid = uid;
        this.factory = factory;
        this.isLogDebug = LOG.isDebugEnabled();
    }

    public void addChange(FunctionChangeRow change) {
        if (changes == null) {
            this.changes = new LinkedGroupingListMap<>();
        }
        changes.put(change.commit, change);
    }

    public Optional<LinkedGroupingListMap<Commit, FunctionChangeRow>> getChanges() {
        return Optional.ofNullable(changes);
    }

    public void markSameAs(FunctionInBranch function) {
        if (isSameAs(function)) return;
        if (isLogDebug) {
            LOG.debug("Marking as the same function: " + function + " and " + this);
        }
        factory.markAsSame(this, function);
    }

    public boolean isSameAs(FunctionInBranch other) {
        return ((this == other) || (this.uid == other.uid));
    }

    @Override
    public String toString() {
        return "FunctionInBranch{" +
                "firstId=" + firstId +
                '}';
    }

    public Optional<Map<Commit, AbResRow>> getJointFunctionAbSmellRows() {
        return Optional.ofNullable(this.jointFunctionAbSmellRows);
    }

    public void addJointFunctionAbSmellRow(Commit commit, AbResRow jointFunctionAbSmellRow) {
        if (this.jointFunctionAbSmellRows == null) {
            this.jointFunctionAbSmellRows = new LinkedHashMap<>();
        }
        this.jointFunctionAbSmellRows.put(commit, jointFunctionAbSmellRow);
    }
}
