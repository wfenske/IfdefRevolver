package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

class FunctionInBranch {
    private static Logger LOG = Logger.getLogger(FunctionInBranch.class);
    private final FunctionInBranchFactory factory;
    int uid;
    private final FunctionId firstId;
    private List<FunctionChangeRow> changes = new ArrayList<>();

    protected FunctionInBranch(FunctionId firstId, int uid, FunctionInBranchFactory factory) {
        this.firstId = firstId;
        this.uid = uid;
        this.factory = factory;
    }

    public void addChange(FunctionChangeRow change) {
        changes.add(change);
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
}
