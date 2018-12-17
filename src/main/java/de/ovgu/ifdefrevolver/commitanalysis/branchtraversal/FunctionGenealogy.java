package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.skunk.util.LinkedGroupingListMap;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

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

    public boolean existsAtStartOfSnapshot(Snapshot s) {
        return jointFunctionAbSmellRowsBySnapshot.containsKey(s);
    }

    public OptionalInt countCommitsInSnapshot(Snapshot s) {
        if (!existsAtStartOfSnapshot(s)) return OptionalInt.empty();
        int result = getChangingCommitsInSnapshot(s).size();
        return OptionalInt.of(result);
    }

    protected LinkedHashSet<Commit> getChangingCommitsInSnapshot(Snapshot s) {
        final List<FunctionChangeRow> changes = changesBySnapshot.get(s);
        return changes.stream().map(c -> c.commit).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public OptionalInt countLinesAddedInSnapshot(Snapshot s) {
        return sumChangeRowsInSnapshot(s, c -> c.linesAdded);
    }

    public OptionalInt countLinesDeletedInSnapshot(Snapshot s) {
        return sumChangeRowsInSnapshot(s, c -> c.linesDeleted);
    }

    public OptionalInt countLinesChangedInSnapshot(Snapshot s) {
        return sumChangeRowsInSnapshot(s, c -> c.linesAdded + c.linesDeleted);
    }

    private OptionalInt sumChangeRowsInSnapshot(Snapshot s, ToIntFunction<FunctionChangeRow> functionChangeRowToIntFunction) {
        if (!existsAtStartOfSnapshot(s)) return OptionalInt.empty();

        final List<FunctionChangeRow> changes = changesBySnapshot.get(s);
        int result = changes.stream().mapToInt(functionChangeRowToIntFunction).sum();
        return OptionalInt.of(result);
    }

    public OptionalInt distanceToMostRecentEdit(Snapshot s) {
        if (!existsAtStartOfSnapshot(s)) return OptionalInt.empty();

        Commit startCommit = s.getStartCommit();
        Set<Commit> newestChangingCommits = getNewestChangingCommitsBefore(startCommit);
        return newestChangingCommits.stream().mapToInt(c -> c.minDistance(startCommit).get()).min();
    }

    public OptionalInt age(Snapshot s) {
        if (!existsAtStartOfSnapshot(s)) return OptionalInt.empty();

        Commit startCommit = s.getStartCommit();
        Set<Commit> oldestChangingCommits = getOldestChangingCommitsBefore(startCommit);
        return oldestChangingCommits.stream().mapToInt(c -> c.minDistance(startCommit).get()).max();
    }

    private Set<Commit> getNewestChangingCommitsBefore(final Commit point) {
        Set<Commit> allChaningCommitsBefore = getAllChaningCommitsBefore(point);

        Set<Commit> result = new HashSet<>();
        for (Commit c : allChaningCommitsBefore) {
            if (allChaningCommitsBefore.stream().noneMatch(descendant -> (descendant != c) && descendant.isDescendant(c))) {
                result.add(c);
            }
        }

        return result;
    }

    private Set<Commit> getOldestChangingCommitsBefore(final Commit point) {
        Set<Commit> allChaningCommitsBefore = getAllChaningCommitsBefore(point);

        Set<Commit> result = new HashSet<>();
        for (Commit c : allChaningCommitsBefore) {
            if (allChaningCommitsBefore.stream().noneMatch(ancestor -> (ancestor != c) && c.isDescendant(ancestor))) {
                result.add(c);
            }
        }

        return result;
    }

    private Set<Commit> getAllChaningCommitsBefore(Commit point) {
        Set<Commit> allOlderCommits = new HashSet<>();

        for (Map.Entry<Snapshot, List<FunctionChangeRow>> e : changesBySnapshot.getMap().entrySet()) {
            e.getValue().forEach(change -> allOlderCommits.add(change.commit));
        }
        allOlderCommits.remove(point);

        for (Iterator<Commit> olderCommitIter = allOlderCommits.iterator(); olderCommitIter.hasNext(); ) {
            if (!point.isDescendant(olderCommitIter.next())) {
                olderCommitIter.remove();
            }
        }

        return allOlderCommits;
    }

    @Override
    public String toString() {
        final int numSnapshots = changesBySnapshot.getMap().keySet().size();
        return "FunctionGenealogy{" + firstId + ", #snapshots=" + numSnapshots + '}';
    }
}
