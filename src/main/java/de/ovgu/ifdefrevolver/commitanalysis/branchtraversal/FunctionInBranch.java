package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;


import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.skunk.util.LinkedGroupingListMap;
import org.apache.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

class FunctionInBranch {
    private static Logger LOG = Logger.getLogger(FunctionInBranch.class);
    private final FunctionInBranchFactory factory;
    int uid;
    public final FunctionId firstId;
    private Map<Commit, JointFunctionAbSmellRow> jointFunctionAbSmellRows = new LinkedHashMap<>();
    private LinkedGroupingListMap<Commit, FunctionChangeRow> changes = new LinkedGroupingListMap<>();

    protected FunctionInBranch(FunctionId firstId, int uid, FunctionInBranchFactory factory) {
        this.firstId = firstId;
        this.uid = uid;
        this.factory = factory;
    }

    public void addChange(FunctionChangeRow change) {
        changes.put(change.commit, change);
    }

    public LinkedGroupingListMap<Commit, FunctionChangeRow> getChanges() {
        return changes;
    }

    public void markSameAs(FunctionInBranch function) {
        if (function == this) return;
        LOG.debug("Marking as the same function: " + function + " and " + this);
        factory.markAsSame(this, function);
    }

    public boolean isSameAs(FunctionInBranch other) {
        return this.uid == other.uid;
    }

    @Override
    public String toString() {
        return "FunctionInBranch{" +
                "firstId=" + firstId +
                '}';
    }

    public Map<Commit, JointFunctionAbSmellRow> getJointFunctionAbSmellRows() {
        return this.jointFunctionAbSmellRows;
    }

    public void addJointFunctionAbSmellRow(JointFunctionAbSmellRow jointFunctionAbSmellRow) {
        this.jointFunctionAbSmellRows.put(jointFunctionAbSmellRow.commit, jointFunctionAbSmellRow);
    }
}
