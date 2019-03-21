package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.AbResRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.commitanalysis.IAbResRow;
import de.ovgu.skunk.util.LinkedGroupingListMap;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FunctionGenealogy {
    private static final OptionalInt OPTIONAL_ZERO = OptionalInt.of(0);
    private static Logger LOG = Logger.getLogger(FunctionGenealogy.class);

    private final int uid;
    private final FunctionId firstId;
    private final Set<FunctionId> functionIds;
    private final Map<Snapshot, AbResRow> jointFunctionAbSmellRowsBySnapshot;
    private final LinkedGroupingListMap<Snapshot, FunctionChangeRow> changesBySnapshot;

    public FunctionGenealogy(int uid, Set<FunctionId> functionIds, Map<Snapshot, AbResRow> jointFunctionAbSmellRowsBySnapshot, LinkedGroupingListMap<Snapshot, FunctionChangeRow> changesBySnapshot) {
        this.uid = uid;
        this.functionIds = functionIds;
        this.jointFunctionAbSmellRowsBySnapshot = jointFunctionAbSmellRowsBySnapshot;
        this.changesBySnapshot = changesBySnapshot;
        this.firstId = functionIds.iterator().next();
    }

    public int getUid() {
        return uid;
    }

    public FunctionId getFirstId() {
        return firstId;
    }

    public boolean existsAtStartOfSnapshot(Snapshot s) {
        return jointFunctionAbSmellRowsBySnapshot.containsKey(s);
    }

    public AbResRow getStaticMetrics(Snapshot s) {
        assertExistsAtStartOfSnapshot(s);
        return jointFunctionAbSmellRowsBySnapshot.get(s);
    }

    private int countCommitsInSnapshot(Snapshot s) {
        assertExistsAtStartOfSnapshot(s);
        final List<FunctionChangeRow> changes = changesBySnapshot.get(s);
        return (int) changes.stream()
                .filter(FunctionChangeRow::isModOrMove)
                .map(c -> c.commit)
                .distinct()
                .count();
    }

    private void assertExistsAtStartOfSnapshot(Snapshot s) {
        if (!existsAtStartOfSnapshot(s)) {
            throw new IllegalArgumentException("Function does not exist at the beginning of snapshot: " +
                    "function=" + this + ", snapshot=" + s);
        }
    }

    private int countLinesAddedInSnapshot(Snapshot s) {
        return sumChangeRowsInSnapshot(s, c -> c.linesAdded);
    }

    private int countLinesDeletedInSnapshot(Snapshot s) {
        return sumChangeRowsInSnapshot(s, c -> c.linesDeleted);
    }

    private int countLinesChangedInSnapshot(Snapshot s) {
        return sumChangeRowsInSnapshot(s, FunctionChangeRow::linesChanged);
    }

    private int sumChangeRowsInSnapshot(Snapshot s, ToIntFunction<FunctionChangeRow> functionChangeRowToIntFunction) {
        assertExistsAtStartOfSnapshot(s);

        final List<FunctionChangeRow> changes = changesBySnapshot.get(s);
        int result = sumAttrValuesOfChanges(changes, functionChangeRowToIntFunction);

        return result;
    }

    private int sumAttrValuesOfChanges(List<FunctionChangeRow> changes, ToIntFunction<FunctionChangeRow> functionChangeRowToIntFunction) {
        return changes.stream()
                .filter(FunctionChangeRow::isModOrMove)
                .mapToInt(functionChangeRowToIntFunction)
                .sum();
    }

    public OptionalInt distanceToMostRecentEdit(Snapshot s) {
        assertExistsAtStartOfSnapshot(s);

        Commit startCommit = s.getStartCommit();
        Set<Commit> newestChangingCommitsBeforeStartCommit = getNewestChangingCommitsBefore(startCommit);

        if (!newestChangingCommitsBeforeStartCommit.isEmpty()) {
            return newestChangingCommitsBeforeStartCommit.stream()
                    .mapToInt(c -> startCommit.distanceAmongCModifyingCommits(c).get())
                    .min();
        }

        if (haveChangesForOlderSnapshotThan(s)) {
            LOG.warn("Failed to compute edit distance. function=" + this + ", snapshot=" + s);
            return OptionalInt.empty();
        } else {
            LOG.debug("Assuming edit distance 0 because snapshot is the first one in which this function exists. function=" + this + ", snapshot=" + s);
            return OPTIONAL_ZERO;
        }
    }

    public OptionalInt age(Snapshot s) {
        assertExistsAtStartOfSnapshot(s);

        Commit startCommit = s.getStartCommit();
        Set<Commit> oldestChangingCommits = getOldestChangingCommitsBefore(startCommit);
        if (!oldestChangingCommits.isEmpty()) {
            return oldestChangingCommits.stream()
                    .mapToInt(c -> startCommit.distanceAmongCModifyingCommits(c).get())
                    .max();
        }

        if (haveChangesForOlderSnapshotThan(s)) {
            LOG.warn("Failed to compute age. function=" + this + ", snapshot=" + s);
            return OptionalInt.empty();
        } else {
            LOG.debug("Assuming age 0 because snapshot is the first one in which this function exists. function=" + this + ", snapshot=" + s);
            return OPTIONAL_ZERO;
        }
    }

    private boolean haveChangesForOlderSnapshotThan(Snapshot needle) {
        return changesBySnapshot.getMap().keySet().stream().anyMatch(other -> other.compareTo(needle) < 0);
    }

    private Set<Commit> getNewestChangingCommitsBefore(final Commit point) {
        Set<Commit> allChangingCommitsBefore = getAllChangingCommitsBefore(point);

        Set<Commit> result = new HashSet<>();
        for (Commit c : allChangingCommitsBefore) {
            if (allChangingCommitsBefore.stream().noneMatch(descendant -> (descendant != c) && descendant.isDescendantOf(c))) {
                result.add(c);
            }
        }

        return result;
    }

    private Set<Commit> getOldestChangingCommitsBefore(final Commit point) {
        Set<Commit> allChangingCommitsBefore = getAllChangingCommitsBefore(point);

        Set<Commit> result = new HashSet<>();
        for (Commit c : allChangingCommitsBefore) {
            if (allChangingCommitsBefore.stream().noneMatch(ancestor -> (ancestor != c) && ancestor.isAncestorOf(c))) {
                result.add(c);
            }
        }

        return result;
    }

    private Set<Commit> getAllChangingCommitsBefore(Commit point) {
        Set<Commit> allChangingCommits = changesBySnapshot.getMap()
                .values()
                .stream()
                .flatMap(changeRows -> changeRows.stream())
                .map(change -> change.commit)
                .collect(Collectors.toCollection(HashSet::new));

        allChangingCommits.remove(point);

        for (Iterator<Commit> it = allChangingCommits.iterator(); it.hasNext(); ) {
            final Commit ancestor = it.next();
            if (!point.isDescendantOf(ancestor)) {
                it.remove();
            }
        }

        return allChangingCommits;
    }

    @Override
    public String toString() {
        final int numSnapshots = changesBySnapshot.getMap().keySet().size();
        final StringBuilder sb = new StringBuilder("FunctionGenealogy{");
        sb.append("uid=").append(uid);
        sb.append(", firstId=").append(firstId);
        sb.append(", numSnapshots=").append(numSnapshots);
        sb.append('}');
        return sb.toString();
    }

    public OptionalInt age(List<Snapshot> window) {
        return age(window.get(0));
    }

    public OptionalInt distanceToMostRecentEdit(List<Snapshot> window) {
        return distanceToMostRecentEdit(window.get(0));
    }

    private int sumAttrValuesInWindow(List<Snapshot> window, ToIntFunction<Snapshot> getAttr) {
        int first = getAttr.applyAsInt(window.get(0));
        if (window.size() == 1) {
            return first;
        } else {
            int rest = window.stream()
                    .skip(1)
                    .filter(s -> existsAtStartOfSnapshot(s))
                    .mapToInt(getAttr)
                    .sum();
            return first + rest;
        }
    }

    public int countCommitsInWindow(List<Snapshot> window) {
        return sumAttrValuesInWindow(window, s -> countCommitsInSnapshot(s));
    }

    public int countCommitsInPreviousSnapshots(List<Snapshot> window) {
        Stream<FunctionChangeRow> changesInPreviousSnapshots = getModsAndMovesPreviousSnapshots(window);
        return (int) changesInPreviousSnapshots
                .map(c -> c.commit)
                .distinct()
                .count();
    }

    public int countLinesChangedInPreviousSnapshots(List<Snapshot> window) {
        Stream<FunctionChangeRow> changesInPreviousSnapshots = getModsAndMovesPreviousSnapshots(window);
        return changesInPreviousSnapshots
                .mapToInt(FunctionChangeRow::linesChanged)
                .sum();
    }

    private Stream<FunctionChangeRow> getModsAndMovesPreviousSnapshots(List<Snapshot> window) {
        Snapshot firstSnapshotOfWindow = window.get(0);
        assertExistsAtStartOfSnapshot(firstSnapshotOfWindow);
        return this.changesBySnapshot.getMap()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().compareTo(firstSnapshotOfWindow) < 0)
                .flatMap(entry -> entry.getValue().stream())
                .filter(FunctionChangeRow::isModOrMove);
    }

    public int countLinesChangedInWindow(List<Snapshot> window) {
        return sumAttrValuesInWindow(window, s -> countLinesChangedInSnapshot(s));
    }

    public int countLinesAddedInWindow(List<Snapshot> window) {
        return sumAttrValuesInWindow(window, s -> countLinesAddedInSnapshot(s));
    }

    public int countLinesDeletedInWindow(List<Snapshot> window) {
        return sumAttrValuesInWindow(window, s -> countLinesDeletedInSnapshot(s));
    }

    public IAbResRow getStaticMetrics(List<Snapshot> window) {
        final AbResRow first = getStaticMetrics(window.get(0));
//        if (window.size() == 1) {
//            return first;
//        } else {
//            List<AbResRow> rest = window.stream()
//                    .skip(1)
//                    .filter(s -> existsAtStartOfSnapshot(s))
//                    .map(this::getStaticMetrics).collect(Collectors.toList());
//            return new AggregatedAbResRow(first, rest);
//        }
        return first;
    }
}
