package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.skunk.util.LinkedGroupingListMap;

import java.util.*;

public class FunctionGenealogy {
    private final FunctionId firstId;
    private final Set<FunctionId> functionIds;
    private final Map<Snapshot, JointFunctionAbSmellRow> jointFunctionAbSmellRowsBySnapshot;
    private final LinkedGroupingListMap<Snapshot, FunctionChangeRow> changesBySnapshot;

    public FunctionGenealogy(Set<FunctionId> functionIds, Map<Snapshot, JointFunctionAbSmellRow> jointFunctionAbSmellRowsBySnapshot, LinkedGroupingListMap<Snapshot, FunctionChangeRow> changesBySnapshot) {
        this.functionIds = functionIds;
        this.jointFunctionAbSmellRowsBySnapshot = jointFunctionAbSmellRowsBySnapshot;
        this.changesBySnapshot = changesBySnapshot;
        this.firstId = functionIds.iterator().next();
    }

    public FunctionId getFirstId() {
        return firstId;
    }

    public Optional<Integer> countCommitsInSnapshot(Snapshot s) {
        final List<FunctionChangeRow> changes = changesBySnapshot.get(s);
        if (changes == null) return Optional.empty();
        Set<CommitsDistanceDb.Commit> commits = new HashSet<>();
        for (FunctionChangeRow r : changes) {
            commits.add(r.commit);
        }
        return Optional.of(commits.size());
    }

    @Override
    public String toString() {
        final int numSnapshots = changesBySnapshot.getMap().keySet().size();
        return "FunctionGenealogy{" + firstId + ", #snapshots=" + numSnapshots + '}';
    }
}
