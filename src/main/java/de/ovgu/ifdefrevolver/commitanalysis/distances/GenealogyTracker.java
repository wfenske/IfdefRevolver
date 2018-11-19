package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.correlate.data.IHasSnapshotDate;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.*;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import de.ovgu.skunk.detection.output.CsvRowProvider;
import de.ovgu.skunk.util.GroupingHashSetMap;
import de.ovgu.skunk.util.GroupingListMap;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GenealogyTracker {
    private static Logger LOG = Logger.getLogger(GenealogyTracker.class);
    private final List<FunctionChangeRow>[] changesByCommitKey;
    private final ListChangedFunctionsConfig config;

    private CommitsDistanceDb commitsDistanceDb;
    private Queue<Commit> next;
    private BitSet done;
    private Branch currentBranch;
    private Commit currentCommit;
    private final FunctionInBranchFactory functionFactory;
    private Branch[] branchesByCommitKey;
    private MoveConflictStats moveConflictStats;

    private static class MoveConflictStats {
        public int allMoveConflicts = 0;
        public int moveConflictsThatProbablyResolveMergeConflicts = 0;
    }

    private static class FunctionInBranchFactory {
        private Queue<Integer> unusedUids = new LinkedList<>();
        private int nextUid = 0;
        private GroupingHashSetMap<Integer, FunctionInBranch> functionsByUid = new GroupingHashSetMap<>();

        public synchronized FunctionInBranch create(FunctionId firstId) {
            int uid = getNextUnusedUid();
            FunctionInBranch result = new FunctionInBranch(firstId, uid, this);
            functionsByUid.put(uid, result);
            return result;
        }

        public synchronized void markAsSame(FunctionInBranch a, FunctionInBranch b) {
            final int aUid = a.uid;
            final int bUid = b.uid;
            if (aUid == bUid) return;

            if (!unusedUids.contains(bUid)) {
                unusedUids.offer(bUid);
            }

            final Set<FunctionInBranch> functionsWithSameUidAsB = functionsByUid.remove(bUid);
            for (FunctionInBranch functionLikeB : functionsWithSameUidAsB) {
                functionLikeB.uid = aUid;
                functionsByUid.put(aUid, functionLikeB);
            }
        }

        private int getNextUnusedUid() {
            if (unusedUids.isEmpty()) {
                return nextUid++;
            } else {
                return unusedUids.poll();
            }
        }
    }

    private static class FunctionInBranch {
        private final FunctionInBranchFactory factory;
        private int uid;
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

    private static class DeletionRecord {
        public final FunctionInBranch function;
        public final Commit deletingCommit;
        public final Branch branch;
        private boolean active = true;

        public static List<DeletionRecord> excludeSuperseded(List<DeletionRecord> records) {
            final boolean logDebug = LOG.isDebugEnabled();

            List<DeletionRecord> result = new ArrayList<>();
            Set<Commit> allCommits = records.stream().map(r -> r.deletingCommit).collect(Collectors.toSet());

            for (DeletionRecord r : records) {
                final Commit deletingCommit = r.deletingCommit;
                if (allCommits.stream().noneMatch(otherCommit -> (otherCommit != deletingCommit) && otherCommit.isDescendant(deletingCommit))) {
                    result.add(r);
                } else {
                    if (logDebug) {
                        Set<String> supersedingCommits = allCommits.stream().filter(otherCommit -> (otherCommit != deletingCommit) && otherCommit.isDescendant(deletingCommit)).map(c -> c.commitHash).collect(Collectors.toSet());
                        LOG.debug("Deletion record " + r + " is superseded by at least one other deletion record in " + records);
                        LOG.debug("Current deleting commit: " + deletingCommit.commitHash + " superseding commits=" + supersedingCommits);
                    }
                }
            }

            return result;
        }

        public static class DeletionRecordSummary {
            public final int numActiveDeletes;
            public final int numInactiveDeletes;

            private DeletionRecordSummary(int numActiveDeletes, int numInactiveDeletes) {
                this.numActiveDeletes = numActiveDeletes;
                this.numInactiveDeletes = numInactiveDeletes;
            }

            public boolean isAlwaysDeleted() {
                return numInactiveDeletes == 0;
            }

            public boolean isNeverDeleted() {
                return numActiveDeletes == 0;
            }

            public boolean isAmbiguous() {
                return (numActiveDeletes != 0) && (numInactiveDeletes != 0);
            }
        }

        public static DeletionRecordSummary summarizeRecords(Collection<DeletionRecord> records) {
            int numActive = 0;
            int numInactive = 0;

            for (DeletionRecord r : records) {
                if (r.isActive()) numActive++;
                else numInactive++;
            }

            return new DeletionRecordSummary(numActive, numInactive);
        }

        private DeletionRecord(FunctionInBranch function, Commit deletingCommit, Branch branch) {
            this.function = function;
            this.deletingCommit = deletingCommit;
            this.branch = branch;
        }

        public boolean isActive() {
            return active;
        }

        public void deactivate() {
            this.active = false;
        }

        @Override
        public String toString() {
            return "DeletionRecord{" +
                    "function=" + function +
                    ", deletingCommit=" + deletingCommit +
                    ", active=" + active +
                    ", branch=" + branch +
                    '}';
        }
    }

    private static class FunctionsInBranch {
        protected Branch branch;
        protected final MoveConflictStats moveConflictStats;
        protected final FunctionInBranchFactory functionFactory;

        private Map<FunctionId, FunctionInBranch> functionsById = new HashMap<>();
        //private Map<FunctionId, FunctionInBranch> added = new HashMap<>();
        private Map<FunctionId, DeletionRecord> deleted = new HashMap<>();
        private GroupingListMap<FunctionId, FunctionChangeRow> movedInCurrentBranch = new GroupingListMap<>();
        /**
         * Where we save functions that have somehow been replaced because we parsed the changes wrong.
         */
        private Set<FunctionInBranch> overwritten = new HashSet<>();

        public FunctionsInBranch(MoveConflictStats moveConflictStats, FunctionInBranchFactory functionFactory) {
            this.moveConflictStats = moveConflictStats;
            this.functionFactory = functionFactory;
        }

        public void putAdd(FunctionChangeRow change) {
            final FunctionId functionId = change.functionId;
            LOG.debug("ADD " + functionId + " by " + change.commit.commitHash + " in " + this.branch);

            final FunctionInBranch theFunction;

            FunctionInBranch oldFunction = functionsById.get(functionId);
            if (oldFunction != null) {
                LOG.debug("Added function already exists: " + functionId);
//                FunctionInBranch replacedAdd = added.put(functionId, oldFunction);
//                if ((replacedAdd != null) && (replacedAdd != oldFunction)) {
//                    LOG.warn("Overwriting a previous add for ID " + functionId);
//                    overwritten.add(replacedAdd);
//                }
                markNotDeleted(functionId, oldFunction, change.commit);
            } else {
                FunctionInBranch revivedFunction = undeleteFunction(functionId, change.commit);
                if (revivedFunction != null) {
                    functionsById.put(functionId, revivedFunction);
                    //added.put(functionId, revivedFunction);
                } else {
                    FunctionInBranch newFunction = functionFactory.create(functionId);
                    functionsById.put(functionId, newFunction);
                    //added.put(functionId, newFunction);
                    markNotDeleted(functionId, newFunction, change.commit);
                }
            }
        }

        private FunctionInBranch undeleteFunction(FunctionId functionId, Commit commit) {
            DeletionRecord lastRecord = getLastDeletionRecord(functionId);
            if (lastRecord == null) return null;

            if (!lastRecord.isActive()) {
                LOG.debug("Function was previously deleted but currently is not: " + functionId);
            } else {
                if (isRecordInCurrentBranch(lastRecord)) {
                    LOG.debug("Function was deleted in current branch but is resurrected by " + commit.commitHash + " : " + functionId);
                } else {
                    LOG.debug("Function was deleted in a previous branch but is resurrected by " + commit.commitHash + " : " + functionId);
                }
                DeletionRecord newRecord = deleteFunction(functionId, lastRecord.function, commit);
                newRecord.deactivate();
            }

            return lastRecord.function;
        }

        private void markNotDeleted(FunctionId functionId, FunctionInBranch function, Commit commit) {
            DeletionRecord record = getLastDeletionRecord(functionId);
            if ((record != null) && (record.deletingCommit == commit) && (record.function == function)) {
                record.deactivate();
            } else {
                DeletionRecord newRecord = deleteFunction(functionId, function, commit);
                newRecord.deactivate();
            }
        }

        private boolean isFunctionDeleted(FunctionId functionId) {
            DeletionRecord lastRecord = getLastDeletionRecord(functionId);
            if (lastRecord == null) return false;
            return lastRecord.isActive();
        }

        private boolean isFunctionDeletedInThisBranch(FunctionId functionId) {
            DeletionRecord lastRecord = getLastActiveDeletionRecordInThisBranch(functionId);
            return (lastRecord != null);
        }

        private DeletionRecord getLastActiveDeletionRecordInThisBranch(FunctionId functionId) {
            DeletionRecord lastRecord = getLastDeletionRecordInThisBranch(functionId);
            if ((lastRecord != null) && lastRecord.isActive()) return lastRecord;
            else return null;
        }

        private DeletionRecord getLastDeletionRecordInThisBranch(FunctionId functionId) {
            DeletionRecord lastRecord = getLastDeletionRecord(functionId);
            if ((lastRecord != null) && isRecordInCurrentBranch(lastRecord)) return lastRecord;
            return null;
        }

        private boolean isRecordInCurrentBranch(DeletionRecord lastRecord) {
            return this.branch.directlyContains(lastRecord.deletingCommit);
        }

        private DeletionRecord getLastDeletionRecord(FunctionId functionId) {
            DeletionRecord deletionRecord = this.deleted.get(functionId);
            if (deletionRecord == null) return null;
            return deletionRecord;
        }

        public void putMod(FunctionChangeRow change) {
            final FunctionId id = change.functionId;

            LOG.debug("MOD " + id + " by " + change.commit.commitHash + " in " + this.branch);

            FunctionInBranch function = functionsById.get(id);
            if (function == null) {
                if (isFunctionDeletedInThisBranch(id)) {
                    LOG.info("Ignoring mod to function that was deleted by a previous commit in this branch. change=" + change + " branch=" + branch);
                    return;
                }
                function = findRenamedFunction(id);
            }

            if (isFunctionDeleted(id)) {
                LOG.warn("MOD to deleted function " + id + " by " + change.commit.commitHash + " in " + this.branch);
            }

            if (function == null) {
                function = undeleteFunction(id, change.commit);
                if (function == null) {
                    function = findFunctionInParentBranches(id);
                }
                if (function != null) {
                    functionsById.put(id, function);
                }
            }

            if (function == null) {
                LOG.info("Modified function does not exist: " + id);
                function = functionFactory.create(id);
                functionsById.put(id, function);
                //added.put(id, function);
            }
            function.addChange(change);

            markNotDeleted(id, function, change.commit);
        }

        private FunctionInBranch findFunctionInParentBranches(FunctionId id) {
            Collection<Branch> seen = new ArrayList<>();
            Queue<Branch> parentBranches = new LinkedList<>();
            for (Branch parent : this.branch.parentBranches) {
                parentBranches.offer(parent);
            }

            Branch b;
            while ((b = parentBranches.poll()) != null) {
                if (seen.contains(b)) continue;

                FunctionInBranch f = b.functions.functionsById.get(id);
                if (f != null) {
                    LOG.info("Remapping change to a function in a parent branch: " + id + " current branch=" + this.branch + " parent branch=" + b);
                    return f;
                }

                for (Branch parent : b.parentBranches) {
                    parentBranches.offer(parent);
                }
                seen.add(b);
            }

            return null;
        }

        private FunctionInBranch findRenamedFunction(FunctionId id) {
            List<FunctionChangeRow> moves = movedInCurrentBranch.get(id);
            if (moves == null) return null;

            ListIterator<FunctionChangeRow> reverseIter = moves.listIterator(moves.size());
            while (reverseIter.hasPrevious()) {
                final FunctionChangeRow move = reverseIter.previous();
                FunctionId newId = move.newFunctionId.get();
                FunctionInBranch newFunction = functionsById.get(newId);
                LOG.warn("Remapping change to a renamed function: oldId=" + id + " newId=" + newId + " branch=" + branch);
                if (newFunction != null) return newFunction;
            }
            return null;
        }

        public void putMove(FunctionChangeRow change) {
            final FunctionId oldFunctionId = change.functionId;
            final FunctionId newFunctionId = change.newFunctionId.get();

            if (oldFunctionId.equals(newFunctionId)) {
                LOG.debug("MOVE is actually a MOD (both ids are the same): " + change);
                putMod(change);
                return;
            }

            LOG.debug("MOVE " + oldFunctionId + " -> " + newFunctionId + " by " + change.commit.commitHash + " in " + this.branch);

            FunctionInBranch existingFunction = functionsById.remove(oldFunctionId);
            FunctionInBranch conflictingFunction = functionsById.get(newFunctionId);

            if ((existingFunction != null) && (conflictingFunction == null)) { // normal case
                continueRegularMove(change, oldFunctionId, newFunctionId, existingFunction);
            } else {
                continueBrokenMove(change, existingFunction, conflictingFunction);
            }
            movedInCurrentBranch.put(oldFunctionId, change);
        }

        private void continueRegularMove(FunctionChangeRow change, FunctionId oldFunctionId, FunctionId newFunctionId, FunctionInBranch existingFunction) {
            existingFunction.addChange(change);
            functionsById.put(newFunctionId, existingFunction);
            updateAddedAfterMove(oldFunctionId, newFunctionId);
            updateDeletedAfterMove(change, oldFunctionId, newFunctionId, existingFunction);
        }

        private void continueBrokenMove(FunctionChangeRow change, FunctionInBranch existingFunction, FunctionInBranch conflictingFunction) {
            final FunctionId oldFunctionId = change.functionId;
            final FunctionId newFunctionId = change.newFunctionId.get();

            if ((existingFunction == null) && (conflictingFunction != null) && moveAlreadyHappenedInCurrentBranch(oldFunctionId, newFunctionId)) {
                if (moveAlreadyHappenedForSameCommit(change)) {
                    LOG.info("Ignoring duplicate MOVE " + change + " due to an identical MOVE in the same commit. " + this.branch);
                } else {
                    LOG.warn("Ignoring MOVE " + change + " due to a prior identical MOVE in the same branch. " + this.branch);
                }
                return;
            }

            final boolean probableConflictResolution = changeIsProbablyRelatedToConflicResolutionAfterMerge(change);
            moveConflictStats.allMoveConflicts++;
            if (probableConflictResolution) {
                moveConflictStats.moveConflictsThatProbablyResolveMergeConflicts++;
            }

            if (existingFunction == null) {
                existingFunction = conflictingFunction;
                if (existingFunction != null) {
                    LOG.warn("Moved function already exists under new ID " + newFunctionId);
                } else {
                    LOG.warn("Moved function did not exist under old or new ID " + oldFunctionId + " or " + newFunctionId);
                    existingFunction = undeleteFunction(oldFunctionId, change.commit);
                    if (existingFunction != null) {
                        LOG.warn("Moved function has been resurrected under old id: " + oldFunctionId);
                    } else {
                        existingFunction = undeleteFunction(newFunctionId, change.commit);
                        if (existingFunction != null) {
                            LOG.warn("Moved function has been resurrected under new id: " + newFunctionId);
                        } else {
                            LOG.warn("Moved function has been created fresh");
                            existingFunction = functionFactory.create(newFunctionId);
                        }
                    }
                }
            }

            if ((conflictingFunction != null) && (conflictingFunction != existingFunction)) {
                final String conflictResolution;
                if (probableConflictResolution) {
                    conflictResolution = " (change probably resolves a merge conflict)";
                } else {
                    conflictResolution = " (change probably unrelated to merge conflict)";
                }
                LOG.warn("MOVE conflicts with existing function for new ID " + newFunctionId + " in " + this.branch + conflictResolution);
                overwritten.add(conflictingFunction);
                logMovesInParentBranches(oldFunctionId, newFunctionId);
            }

            continueRegularMove(change, oldFunctionId, newFunctionId, existingFunction);
        }

        private boolean changeIsProbablyRelatedToConflicResolutionAfterMerge(FunctionChangeRow change) {
            return this.branch.isMergeBranch() && (change.commit == this.branch.firstCommit);
        }

        private void logMovesInParentBranches(FunctionId oldFunctionId, FunctionId newFunctionId) {
            if (LOG.isDebugEnabled()) {
                for (Branch parent : this.branch.getParentBranches()) {
                    if (parent.functions.moveAlreadyHappenedInCurrentBranch(oldFunctionId, newFunctionId)) {
                        LOG.debug("An identical MOVE already happened in parent branch " + parent);
                    }
                }
            }
        }

        private boolean moveAlreadyHappenedForSameCommit(FunctionChangeRow move) {
            final List<FunctionChangeRow> moves = movedInCurrentBranch.get(move.functionId);
            if (moves == null) return false;
            return moves.stream().anyMatch(other -> other.isMoveOfIdenticalFunctionIds(move) && other.isSamePreviousRevisionAndCommit(move));
        }

        private boolean moveAlreadyHappenedInCurrentBranch(FunctionId oldFunctionId, FunctionId newFunctionId) {
            final List<FunctionChangeRow> moves = movedInCurrentBranch.get(oldFunctionId);
            if (moves == null) return false;
            return moves.stream().anyMatch(move -> newFunctionId.equals(move.newFunctionId.get()));
        }

        private void updateAddedAfterMove(FunctionId oldFunctionId, FunctionId newFunctionId) {
//            FunctionInBranch oldAdd = added.remove(oldFunctionId);
//            FunctionInBranch replacedAdd = null;
//            if (oldAdd != null) {
//                replacedAdd = added.put(newFunctionId, oldAdd);
//            }
//
//            if ((replacedAdd != null) && (replacedAdd != oldAdd)) {
//                LOG.info("Move conflicts with add for change " + oldFunctionId + " -> " + newFunctionId);
//                overwritten.add(replacedAdd);
//            }
        }

        private void updateDeletedAfterMove(FunctionChangeRow change, FunctionId oldFunctionId, FunctionId newFunctionId, FunctionInBranch existingFunction) {
            deleteFunction(oldFunctionId, existingFunction, change.commit);
            markNotDeleted(newFunctionId, existingFunction, change.commit);
        }

        private DeletionRecord deleteFunction(FunctionId id, FunctionInBranch function, Commit commit) {
            DeletionRecord record = new DeletionRecord(function, commit, branch);
            this.deleted.put(id, record);
            return record;
        }

        public void putDel(FunctionChangeRow change) {
            final FunctionId functionId = change.functionId;
            LOG.debug("DEL " + functionId + " by " + change.commit.commitHash + " in " + this.branch);

//            FunctionInBranch overwrittenAdd = added.remove(functionId);
//            if (overwrittenAdd != null) {
//                overwritten.add(overwrittenAdd);
//            }

            FunctionInBranch oldFunction = functionsById.remove(functionId);
            if ((oldFunction == null) && isFunctionDeleted(functionId)) {
                if (LOG.isInfoEnabled()) {
                    if (isFunctionDeletedInThisBranch(functionId)) {
                        LOG.info("Deleted function was already deleted in this branch. change=" + change);
                    } else {
                        LOG.info("Deleted function was already deleted in a parent branch. change=" + change);
                    }
                }
                return;
            }

            if (oldFunction == null) {
                oldFunction = functionFactory.create(functionId);
                LOG.info("Deleted function did not exist.  Created new dummy function. change=" + change);
            }

            deleteFunction(functionId, oldFunction, change.commit);
        }

        public void merge(PreMergeBranch[] parentBranches, List<FunctionChangeRow> changesOfMergeCommit) {
            if (parentBranches.length == 0) return;

            mergeChangesOfMergeCommits(parentBranches, changesOfMergeCommit);

            final Set<FunctionId> activeDeletes = mergeDeleted(parentBranches);

            GroupingHashSetMap<FunctionId, FunctionInBranch> allNonDeletedFunctionsById = new GroupingHashSetMap<>();

            for (PreMergeBranch parentBranch : parentBranches) {
                for (Map.Entry<FunctionId, FunctionInBranch> e : parentBranch.functions.functionsById.entrySet()) {
                    FunctionId functionId = e.getKey();
                    if (!activeDeletes.contains(functionId)) {
                        allNonDeletedFunctionsById.put(functionId, e.getValue());
                    }
                }
            }

            for (Map.Entry<FunctionId, ? extends Set<FunctionInBranch>> e : allNonDeletedFunctionsById.getMap().entrySet()) {
                final FunctionId functionId = e.getKey();
                final Iterator<FunctionInBranch> valueIt = e.getValue().iterator();
                final FunctionInBranch winner = valueIt.next();
                this.functionsById.put(functionId, winner);
                while (valueIt.hasNext()) {
                    handleDisplacedFunctionDuringBranchMerge(functionId, winner, valueIt.next());
                }
            }

            for (PreMergeBranch parentBranch : parentBranches) {
                inheritDeletedRecords(parentBranch.functions);
            }
        }

        private Set<FunctionId> mergeDeleted(PreMergeBranch[] parentBranches) {
            GroupingListMap<FunctionId, DeletionRecord> lastDeletionRecordsById = new GroupingListMap<>();
            for (PreMergeBranch parent : parentBranches) {
                for (Map.Entry<FunctionId, DeletionRecord> e : parent.functions.deleted.entrySet()) {
                    final FunctionId id = e.getKey();
                    final DeletionRecord lastRecord = e.getValue();
                    lastDeletionRecordsById.put(id, lastRecord);
                }
            }

            final boolean logDebug = LOG.isDebugEnabled();

            final Set<FunctionId> allActiveDeletes = new HashSet<>();

            for (Map.Entry<FunctionId, List<DeletionRecord>> deletionEntry : lastDeletionRecordsById.getMap().entrySet()) {
                final FunctionId id = deletionEntry.getKey();
                final List<DeletionRecord> allRecords = deletionEntry.getValue();

                final List<DeletionRecord> withoutSupersededRecords = DeletionRecord.excludeSuperseded(allRecords);
                final DeletionRecord.DeletionRecordSummary summary = DeletionRecord.summarizeRecords(withoutSupersededRecords);

                if (logDebug) {
                    final DeletionRecord.DeletionRecordSummary allRecordsSummary = DeletionRecord.summarizeRecords(allRecords);
                    if (allRecordsSummary.isAmbiguous() && !summary.isAmbiguous()) {
                        Set<DeletionRecord> supersededRecords = new LinkedHashSet<>(allRecords);
                        supersededRecords.removeAll(withoutSupersededRecords);
                        LOG.debug("Summary of all records is ambiguous but summary of non-superseded records is not. allRecords=" +
                                allRecords + " withoutSupersededRecords=" + withoutSupersededRecords + " supersededRecords=" + supersededRecords);
                    }
                }

                final DeletionRecord winningDeletionRecord = withoutSupersededRecords.get(withoutSupersededRecords.size() - 1);
//                final FunctionInBranch function = winningDeletionRecord.function;
                if (summary.isNeverDeleted()) {
                    LOG.debug("Function is not deleted in any parent branch: " + id + " branch=" + this.branch);
//                    for (DeletionRecord r : withoutSupersededRecords) {
//                        this.deleted.put(id, r);
//                    }
                    //this.markNotDeleted(id, function, this.branch.firstCommit);
                    this.deleted.put(id, winningDeletionRecord);
                } else if (summary.isAlwaysDeleted()) {
                    LOG.info("Function is deleted in all parent branches: " + id + " branch=" + this.branch);
//                    for (DeletionRecord r : withoutSupersededRecords) {
//                        this.deleted.put(id, r);
//                    }
                    //this.deleteFunction(id, function, this.branch.firstCommit);
                    this.deleted.put(id, winningDeletionRecord);
                    allActiveDeletes.add(id);
                } else {
                    LOG.warn("Function is deleted in " + summary.numActiveDeletes + " parent branch(es) but not in " +
                            summary.numInactiveDeletes + " other(s): " + id + " branch=" + this.branch +
                            " deletions=" + withoutSupersededRecords);
                }
            }

            return allActiveDeletes;
        }

        private void mergeChangesOfMergeCommits(PreMergeBranch[] parentBranches, List<FunctionChangeRow> changesOfMergeCommit) {
            mergeChangesOfMergeCommits1(parentBranches, changesOfMergeCommit);

//            changesOfMergeCommit.clear();

            if (!changesOfMergeCommit.isEmpty()) {
                LOG.warn("Merge failed to process " + changesOfMergeCommit.size() + " changes in " + this.branch);
            }
        }

        private void mergeChangesOfMergeCommits1(PreMergeBranch[] parentBranches, List<FunctionChangeRow> changesOfMergeCommit) {
            GroupingListMap<FunctionId, FunctionChangeRow> delsByFunctionId = new GroupingListMap<>();
            for (FunctionChangeRow r : changesOfMergeCommit) {
                delsByFunctionId.put(r.functionId, r);
            }

            for (Iterator<FunctionChangeRow> changeIt = changesOfMergeCommit.iterator(); changeIt.hasNext(); ) {
                final FunctionChangeRow change = changeIt.next();
                if (!change.previousRevision.isPresent()) continue;
                final Commit previousRevision = change.previousRevision.get();
                final FunctionId functionId = change.functionId;
                boolean merged = false;
                for (PreMergeBranch preMergeBranch : parentBranches) {
                    if (preMergeBranch.getLastCommitBeforeMerge() != previousRevision) continue;
                    final FunctionsInBranch preMergeFunctions = preMergeBranch.functions;
                    switch (change.modType) {
                        case MOD:
                            if (preMergeFunctions.functionsById.containsKey(functionId)) {
                                preMergeFunctions.assignChange(change);
                                merged = true;
                            } else {
                                final List<FunctionChangeRow> dels = delsByFunctionId.get(functionId);
                                if ((dels != null) && dels.stream().anyMatch(del -> del.isSamePreviousRevisionAndCommit(change))) {
                                    LOG.info("Ignoring MOD to function that is deleted by another hunk in the same commit: " + change);
                                    merged = true;
                                } else {
                                    LOG.warn("Ignoring MOD to non-existing function: " + change);
                                }
                            }
                            break;
                        default:
                            preMergeFunctions.assignChange(change);
                            merged = true;
                    }

                    if (merged) {
                        changeIt.remove();
                    }
                    break;
                }
            }
        }

        private void handleDisplacedFunctionDuringBranchMerge(FunctionId id, FunctionInBranch function, FunctionInBranch displaced) {
            boolean isSameAlready = function.isSameAs(displaced);
            if (!isSameAlready) {
                function.markSameAs(displaced);
            }

            if (isSameAlready) {
                LOG.debug("Parent branch adds different function for same ID " + id + " during merge of " + this.branch);
            } else {
                LOG.info("Parent branch adds different function for same ID " + id + " during merge of " + this.branch);
            }
        }

        public void inherit(FunctionsInBranch parentFunctions) {
            this.functionsById.putAll(parentFunctions.functionsById);
            inheritDeletedRecords(parentFunctions);
        }

        private void inheritDeletedRecords(FunctionsInBranch parentFunctions) {
            deleted.putAll(parentFunctions.deleted);
        }

        public void logOccurrenceOfFunction(FunctionId id) {
            if (functionsById.containsKey(id)) {
                LOG.warn(id + " is in functionsById in " + this.branch);
            }

            DeletionRecord lastDeletionRecord = getLastDeletionRecord(id);
            if ((lastDeletionRecord != null) && lastDeletionRecord.isActive()) {
                if (isRecordInCurrentBranch(lastDeletionRecord)) {
                    LOG.warn(id + " was deletedInThisBranch by " +
                            lastDeletionRecord.deletingCommit.commitHash +
                            " in " + this.branch);
                } else {
                    LOG.warn(id + " was deleted in a parent branch of " + this.branch);
                }
            }
            if (overwritten.contains(id)) {
                LOG.warn(id + " was overwritten in " + this.branch);
            }
        }

        public void assignChange(FunctionChangeRow change) {
            switch (change.modType) {
                case ADD:
                    putAdd(change);
                    break;
                case MOD:
                    putMod(change);
                    break;
                case MOVE:
                    putMove(change);
                    break;
                case DEL:
                    putDel(change);
                    break;
            }
        }

        public Branch getBranch() {
            return branch;
        }

        public void setBranch(Branch branch) {
            this.branch = branch;
        }

    }

    private static class Branch {
        protected final Branch[] parentBranches;
        protected final Commit firstCommit;
        protected Commit mostRecentCommit;
        protected final MoveConflictStats moveConflictStats;
        protected final FunctionsInBranch functions;
        protected final Set<Commit> directCommits = new LinkedHashSet<>();
        protected final FunctionInBranchFactory functionFactory;
        private Map<Commit, PreMergeBranch> preMergeBranches = new HashMap<>();

        protected Branch(Branch[] parentBranches, Commit firstCommit, MoveConflictStats moveConflictStats, FunctionsInBranch functionsInBranch, FunctionInBranchFactory functionFactory) {
            this.parentBranches = parentBranches;
            this.firstCommit = firstCommit;
            this.mostRecentCommit = firstCommit;
            this.moveConflictStats = moveConflictStats;
            this.functions = functionsInBranch;
            this.functions.setBranch(this);
            this.functionFactory = functionFactory;
        }

        public Branch(Branch[] parentBranches, Commit firstCommit, MoveConflictStats moveConflictStats, FunctionInBranchFactory functionFactory) {
            this(parentBranches, firstCommit, moveConflictStats, new FunctionsInBranch(moveConflictStats, functionFactory), functionFactory);
        }

        public List<Branch> parentsInLevelOrder() {
            Set<Branch> seen = new LinkedHashSet<>();
            Queue<Branch> parentBranches = new LinkedList<>();
            for (Branch parent : this.parentBranches) {
                parentBranches.offer(parent);
            }

            Branch b;
            while ((b = parentBranches.poll()) != null) {
                seen.add(b);
                for (Branch parent : b.parentBranches) {
                    if (!seen.contains(parent)) {
                        parentBranches.offer(parent);
                    }
                }
            }

            return new ArrayList<>(seen);
        }

        @Override
        public String toString() {
            return "Branch{" +
                    "firstCommit='" + firstCommit.commitHash +
                    "', parentBranches=" + parentBranchFirstCommitHashes() +
                    '}';
        }

        private String parentBranchFirstCommitHashes() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < parentBranches.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append('\'');
                sb.append(parentBranches[i].firstCommit.commitHash);
                sb.append('\'');
            }
            sb.append(']');
            return sb.toString();
        }

        public Commit getFirstCommit() {
            return this.firstCommit;
        }

        public Branch[] getParentBranches() {
            return parentBranches;
        }

        public void merge(PreMergeBranch[] parentBranches, List<FunctionChangeRow> mergeChanges) {
            this.functions.merge(parentBranches, mergeChanges);
        }

        public void split(Branch parentBranch) {
            this.functions.inherit(parentBranch.functions);
        }

        public void logOccurrenceOfFunction(FunctionId id) {
            this.functions.logOccurrenceOfFunction(id);
        }


        public void logOccurrenceOfFunctionInSelfAndParentBranches(FunctionId id) {
            logOccurrenceOfFunction(id);
            for (Branch b : this.parentsInLevelOrder()) {
                b.logOccurrenceOfFunction(id);
            }
        }

        public void addDirectCommit(Commit currentCommit) {
            this.directCommits.add(currentCommit);
            this.mostRecentCommit = currentCommit;
        }

        public Commit getMostRecentCommit() {
            return this.mostRecentCommit;
        }

        public boolean directlyContains(Commit commit) {
            return this.directCommits.contains(commit);
        }

        public boolean isMergeBranch() {
            return this.parentBranches.length > 1;
        }

        public static Branch[] toBranchArray(Branch b) {
            Branch[] result = new Branch[1];
            result[0] = b;
            return result;
        }

        public PreMergeBranch createPreMergeBranch(final Commit mergeCommit) {
            PreMergeBranch preMergeBranch = preMergeBranches.get(mergeCommit);
            if (preMergeBranch == null) {
                final Commit lastCommitBeforeMerge = this.getMostRecentCommit();
                preMergeBranch = new PreMergeBranch(this,
                        lastCommitBeforeMerge, mergeCommit, moveConflictStats);
                this.preMergeBranches.put(mergeCommit, preMergeBranch);
            } else {
                throw new IllegalArgumentException("Pre-merge branch for merge " +
                        this.getMostRecentCommit().commitHash + " -> " + mergeCommit.commitHash + " has already been created.");
            }

            return preMergeBranch;
        }

        public PreMergeBranch getPreMergeBranchOrDie(final Commit mergeCommit) {
            PreMergeBranch preMergeBranch = preMergeBranches.get(mergeCommit);
            if (preMergeBranch != null) {
                return preMergeBranch;
            }
            throw new NullPointerException("Pre-merge branch for merge -> " + mergeCommit.commitHash + " does not exist.");
        }
    }

    private static class PreMergeBranch extends Branch {
        private final Commit mergeCommit;

        public PreMergeBranch(Branch actualParentBranchOfMerge, Commit lastCommitBeforeMerge, Commit mergeCommit, MoveConflictStats moveConflictStats) {
            super(toBranchArray(actualParentBranchOfMerge), lastCommitBeforeMerge, moveConflictStats, actualParentBranchOfMerge.functionFactory);
            this.mergeCommit = mergeCommit;
            this.split(actualParentBranchOfMerge);
        }

        public Commit getLastCommitBeforeMerge() {
            return firstCommit;
        }

        public Commit getMergeCommit() {
            return mergeCommit;
        }
    }

    public GenealogyTracker(CommitsDistanceDb commitsDistanceDb, Map<Commit, List<AllFunctionsRow>> allFunctionsBySnapshotStartCommit, List<FunctionChangeRow>[] changesByCommitKey, ListChangedFunctionsConfig config) {
        this.commitsDistanceDb = commitsDistanceDb;
        this.changesByCommitKey = changesByCommitKey;
        this.config = config;
        this.functionFactory = new FunctionInBranchFactory();
    }

    public void main() {
        final int numAllCommits = commitsDistanceDb.getCommits().size();
        moveConflictStats = new MoveConflictStats();

        done = new BitSet(numAllCommits);
        branchesByCommitKey = new Branch[numAllCommits];

        Set<Commit> rootCommits = commitsDistanceDb.getRoots();
        next = new LinkedList<>(rootCommits);
        while (!next.isEmpty()) {
            this.currentCommit = getNextProcessableCommit();
            processCurrentCommit();
            markCurrentCommitAsDone();

            for (Commit child : currentCommit.children()) {
                offerIfNew(child);
            }
        }

        if (done.cardinality() != numAllCommits) {
            throw new RuntimeException("Some unprocessed commits remain");
        }

        LOG.info("Successfully processed all " + changesProcessed + " commits.");
        maybeReportBranchStats();
        LOG.debug("Processed " + changesProcessed + " changes.");

        if (moveConflictStats.allMoveConflicts > 0) {
            int perentage = (int) Math.round((100.0 * moveConflictStats.moveConflictsThatProbablyResolveMergeConflicts) / moveConflictStats.allMoveConflicts);
            LOG.debug("Encountered " + moveConflictStats.allMoveConflicts + " MOVE conflicts of which " +
                    moveConflictStats.moveConflictsThatProbablyResolveMergeConflicts +
                    " (" + perentage + "%) probably resolved a merge conflict.");
        } else {
            LOG.debug("Encountered no MOVE conflicts.");
        }
    }

    protected void maybeReportBranchStats() {
        if (!LOG.isDebugEnabled()) return;

        Set<Branch> branches = new HashSet<>();
        int merges = 0;
        int splits = 0;
        int roots = 0;
        for (Branch id : branchesByCommitKey) {
            boolean wasNew = branches.add(id);
            if (wasNew) {
                switch (id.getParentBranches().length) {
                    case 0:
                        roots++;
                        break;
                    case 1:
                        splits++;
                        break;
                    default:
                        merges++;
                }
            }
        }

        LOG.debug("Created " + branches.size() + " branches. merges=" + merges + ", splits=" + splits + ", roots=" + roots);
    }

    protected void markCurrentCommitAsDone() {
        done.set(currentCommit.key);
    }

    private Commit getNextProcessableCommit() {
        int sz = next.size();
        for (int i = 0; i < sz; i++) {
            Commit nextCommit = next.poll(); // cannot be null due to preconditions of this method
            if (isProcessable(nextCommit)) {
                return nextCommit;
            } else {
                next.offer(nextCommit);
            }
        }
        throw new RuntimeException("None of the next commits is processable");
    }

    private boolean isProcessable(Commit commit) {
        for (Commit parent : commit.parents()) {
            if (!isCommitProcessed(parent)) {
                //LOG.debug(commit + " cannot be processed: Parent " + parent + " has not yet been processed.");
                return false;
            }
        }

//        if (isCommitSiblingOfUnprocessedMerge(commit)) {
//            return false;
//        }

        return true;
    }

//    private boolean isCommitSiblingOfUnprocessedMerge(Commit commit) {
////        for (Commit parent : commit.parents()) {
////            Commit[] children = parent.children();
////            for (Commit child : children) {
////                if (child.isMerge() && (child != commit) && !isCommitProcessed(child)) {
////                    return true;
////                }
////            }
////        }
//        for (Commit sibling : commit.siblings()) {
//            if (sibling.isMerge() && !isCommitProcessed(sibling)) {
//                LOG.debug(commit + " is a sibling of the unprocessed merge commit " + sibling +
//                        ".  Parents: " + Arrays.toString(commit.parents()) + ". Parents of the merge commit: " +
//                        Arrays.toString(sibling.parents()));
//                return true;
//            }
//        }
//        return false;
//    }

    private boolean isCommitProcessed(Commit commit) {
        return done.get(commit.key);
    }

    private void processCurrentCommit() {
//        if (isCommitProcessed(currentCommit)) {
//            throw new RuntimeException("Processing commit " + currentCommit + " a second time.");
//        }

        this.currentBranch = ensureBranch(currentCommit);
        LOG.debug("Processing " + currentCommit + " in " + this.currentBranch);
        this.currentBranch.addDirectCommit(currentCommit);

        if (currentBranch.getFirstCommit() == currentCommit) {
            // This commit is the start of a new branch.  Need to handle merges etc.
            initializeNewBranch(currentBranch);
        }
        processChangesOfCurrentCommit();

        if ((currentBranch.getFirstCommit() == currentCommit) && (currentCommit.isMerge())) {
            validateComputedFunctionsAfterMerge();
        }

        for (Commit child : currentCommit.children()) {
            if (child.isMerge()) {
                this.currentBranch.createPreMergeBranch(child);
            }
        }
    }

    private void validateComputedFunctionsAfterMerge() {
        final Set<FunctionId> actualIds = getActualFunctionIdsAtCurrentCommit();
        final Set<FunctionId> computedIds = new HashSet<>(this.currentBranch.functions.functionsById.keySet());

        final Set<FunctionId> identicalFunctions = new HashSet<>(actualIds);
        identicalFunctions.retainAll(computedIds);

        final Set<FunctionId> functionsThatShouldNotExist = new HashSet<>(computedIds);
        functionsThatShouldNotExist.removeAll(actualIds);

        final Set<FunctionId> functionsThatAreMissing = new HashSet<>(actualIds);
        functionsThatAreMissing.removeAll(computedIds);

        final int numActualIds = actualIds.size();
        LOG.info("After merge: # actual function ids: " + numActualIds + " " + this.currentBranch);
        LOG.info("After merge: # identical function ids: " + identicalFunctions.size() + "/" + numActualIds + " " + this.currentBranch);


        if (functionsThatAreMissing.isEmpty() && functionsThatShouldNotExist.isEmpty()) {
            LOG.info("After merge: Computed and actual function ids match exactly. " + this.currentBranch);
            return;
        }

        LOG.warn("After merge: # computed function ids: " + computedIds.size() + " " + this.currentBranch);
        LOG.warn("After merge: # missing function ids: " + functionsThatAreMissing.size() + "/" + numActualIds + " " + this.currentBranch);
        LOG.warn("After merge: # superfluous function ids: " + functionsThatShouldNotExist.size() + " " + this.currentBranch);

        if (!functionsThatAreMissing.isEmpty()) {
            List<FunctionId> missing = new ArrayList<>(functionsThatAreMissing);
            Collections.sort(missing, FunctionId.BY_FILE_AND_SIGNATURE_ID);
            for (FunctionId id : missing) {
                LOG.warn("After merge: missing: " + id + " " + this.currentBranch);
                currentBranch.logOccurrenceOfFunctionInSelfAndParentBranches(id);
            }
        }

        if (!functionsThatShouldNotExist.isEmpty()) {
            List<FunctionId> superfluous = new ArrayList<>(functionsThatShouldNotExist);
            Collections.sort(superfluous, FunctionId.BY_FILE_AND_SIGNATURE_ID);
            for (FunctionId id : superfluous) {
                LOG.warn("After merge: superfluous: " + id + " " + this.currentBranch);
                currentBranch.logOccurrenceOfFunctionInSelfAndParentBranches(id);
            }
        }

        return;
    }

    private static class CachingFunctionsLister {
        private final ListChangedFunctionsConfig config;

        public CachingFunctionsLister(ListChangedFunctionsConfig config) {
            this.config = config;
        }

        public Set<FunctionId> getFunctionIdsAtCommit(Commit commit) {
            if (cacheFileExists(commit)) {
                return getFunctionIdsFromCsv(commit);
            } else {
                Map<String, List<Method>> actualFunctionsByPath = GitUtil.listFunctionsAtCurrentCommit(this.config.getRepoDir(), commit.commitHash);
                writeCacheCsvFile(actualFunctionsByPath, commit);
                return extractFunctionIds(actualFunctionsByPath);
            }
        }

        private void writeCacheCsvFile(Map<String, List<Method>> actualFunctionsByPath, Commit commit) {
            final File outputFile = AllFunctionsCsvReader.getFileForCommitHash(config, commit.commitHash);
            ensureOutputFileDirOrDie(outputFile);
            Calendar commitDateAsCalendar = GitUtil.getAuthorDateOfCommit(this.config.getRepoDir(), commit.commitHash);
            final Date commitDate = commitDateAsCalendar.getTime();

            CsvFileWriterHelper writer = new CsvFileWriterHelper() {
                IHasSnapshotDate dateProvider = new IHasSnapshotDate() {
                    @Override
                    public Date getSnapshotDate() {
                        return commitDate;
                    }
                };

                CsvRowProvider<Method, IHasSnapshotDate, AllSnapshotFunctionsColumns> csvRowProvider = AllSnapshotFunctionsColumns.newCsvRowProvider(dateProvider);

                @Override
                protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                    csv.printRecord(csvRowProvider.headerRow());
                    try {
                        printRecordsForFunctions(csv);
                    } catch (IOException | RuntimeException ex) {
                        LOG.warn("Failed to write output file " + outputFile, ex);
                        boolean deleteFailed = false;
                        try {
                            deleteFailed = outputFile.delete();
                        } catch (RuntimeException re) {
                            LOG.warn("Failed to delete incomplete output file " + outputFile, re);
                        }
                        if (deleteFailed) {
                            LOG.warn("Failed to delete output file " + outputFile);
                        }
                    }
                }

                private void printRecordsForFunctions(CSVPrinter csv) throws IOException {
                    for (List<Method> functions : actualFunctionsByPath.values()) {
                        for (Method f : functions) {
                            final Object[] row = csvRowProvider.dataRow(f);
                            csv.printRecord(row);
                        }
                    }
                }
            };

            writer.write(outputFile);
        }

        private void ensureOutputFileDirOrDie(File outputFile) {
            File dir = outputFile.getParentFile();
            if (dir.isDirectory()) {
                return;
            }

            if (!dir.mkdirs()) {
                throw new RuntimeException("Failed to create directory for output file " + outputFile);
            }
        }

        private boolean cacheFileExists(Commit commit) {
            return AllFunctionsCsvReader.fileExists(config, commit.commitHash);
        }

        private Set<FunctionId> extractFunctionIds(Map<String, List<Method>> actualFunctionsByPath) {
            final Set<FunctionId> actualIds = new LinkedHashSet<>();
            for (Map.Entry<String, List<Method>> functionsInPath : actualFunctionsByPath.entrySet()) {
                String path = functionsInPath.getKey();
                for (Method f : functionsInPath.getValue()) {
                    FunctionId id = new FunctionId(f.uniqueFunctionSignature, path);
                    actualIds.add(id);
                }
            }
            return actualIds;
        }

        private Set<FunctionId> getFunctionIdsFromCsv(Commit commit) {
            AllFunctionsCsvReader reader = new AllFunctionsCsvReader();
            final List<AllFunctionsRow> allFunctionsRows = reader.readFile(config, commit.commitHash);
            Set<FunctionId> result = new LinkedHashSet<>();
            for (AllFunctionsRow r : allFunctionsRows) {
                result.add(r.functionId);
            }
            return result;
        }
    }

    private Set<FunctionId> getActualFunctionIdsAtCurrentCommit() {
        boolean logDebug = LOG.isDebugEnabled();
        long timeBefore = 0;
        if (logDebug) {
            timeBefore = System.currentTimeMillis();
        }

        CachingFunctionsLister l = new CachingFunctionsLister(config);
        Set<FunctionId> result = l.getFunctionIdsAtCommit(this.currentCommit);

        if (logDebug) {
            long timeAfter = System.currentTimeMillis();
            long timeInMillis = timeAfter - timeBefore;
            long minutes = timeInMillis / (1000 * 60);
            long seconds = timeInMillis / 1000;
            LOG.debug("Time for checking out revision " + this.currentCommit.commitHash +
                    ": " + minutes + "m" + seconds + "s.");
        }

        return result;
    }

    private void offerIfNew(Commit commit) {
        if (!isCommitProcessed(commit) && !next.contains(commit)) {
            next.offer(commit);
        }
    }

    private Branch ensureBranch(Commit commit) {
        final Branch existingBranch = branchesByCommitKey[commit.key];
        if (existingBranch != null) return existingBranch;

        final Branch branch;
        Commit[] parents = commit.parents();
        if (parents.length == 0) { // a root commit
            Branch[] parentBranches = new Branch[0];
            branch = new Branch(parentBranches, commit, moveConflictStats, functionFactory);
            LOG.debug("Creating new root branch " + branch);
        } else if (parents.length == 1) {
            Commit parent = parents[0];
            if (parent.children().length == 1) {
                branch = getBranchForCommitOrDie(parent);
                LOG.debug("Inheriting branch for " + commit + " from parent commit " + branch);
            } else {
                // Parent splits into multiple branches
                Branch parentBranch = getBranchForCommitOrDie(parent);
                Branch[] parentBranches = Branch.toBranchArray(parentBranch);
                branch = new Branch(parentBranches, commit, moveConflictStats, functionFactory);
                LOG.debug("Creating new branch after branch split " + branch);
            }
        } else { // a merge commit
            Branch[] parentBranches = new Branch[parents.length];
            for (int i = 0; i < parents.length; i++) {
                parentBranches[i] = getBranchForCommitOrDie(parents[i]);
            }
            branch = new Branch(parentBranches, commit, moveConflictStats, functionFactory);
            LOG.debug("Creating new branch after merge " + branch);
        }

        branchesByCommitKey[commit.key] = branch;
        return branch;
    }

    private Branch getBranchForCommitOrDie(Commit commit) {
        Branch id = branchesByCommitKey[commit.key];
        if (id != null) return id;
        throw new IllegalArgumentException(commit + " lacks a branch id");
    }

    private void initializeNewBranch(Branch branch) {
        Branch[] parentBranches = branch.getParentBranches();
        switch (parentBranches.length) {
            case 0:
                initializeNewRootBranch(branch);
                break;
            case 1:
                initializeNewBranchForBranchSplit(branch, parentBranches[0]);
                break;
            default:
                initializeNewMergeBranch(branch, parentBranches);
        }
    }

    private void initializeNewRootBranch(Branch branch) {
        // NOTE: nothing to do
    }

    private void initializeNewMergeBranch(Branch mergeBranch, Branch[] parentBranches) {
        int numParents = parentBranches.length;
        PreMergeBranch[] preMergeBranches = new PreMergeBranch[numParents];
//        final Commit[] parentCommits = mergeBranch.firstCommit.parents();
//        for (int i = 0; i < parentCommits.length; i++) {
//            final Commit parentCommit = parentCommits[i];
//            Branch parentBranch = getBranchForCommitOrDie(parentCommit);
//            final Branch receivedParentBranch = parentBranches[i];
//            if (parentBranch != receivedParentBranch) {
//                throw new RuntimeException("Parent branches mismatch: expected=" + parentBranch + " got=" + receivedParentBranch);
//            }
//            final Commit receivedParentCommit = receivedParentBranch.getMostRecentCommit();
//            if (parentCommit != receivedParentCommit) {
//                throw new RuntimeException("Commits in parent branch mismatch: expected=" + parentCommit + " got=" + receivedParentCommit + ".");
//            }
//        }
        final Commit mergeCommit = mergeBranch.firstCommit;

        Set<Commit> expectedParents = new HashSet<>();
        for (Commit parent : mergeCommit.parents()) {
            expectedParents.add(parent);
        }

        Set<Commit> actualParents = new HashSet<>();

        for (int i = 0; i < numParents; i++) {
            Branch parentOfMerge = parentBranches[i];
            PreMergeBranch preMergeBranch = parentOfMerge.getPreMergeBranchOrDie(mergeCommit);
            if (preMergeBranch.getMergeCommit() != mergeCommit) {
                throw new IllegalArgumentException("Merge commit does not match. Expected=" + mergeCommit.commitHash + " got=" + preMergeBranch.getMergeCommit().commitHash);
            }
            preMergeBranches[i] = preMergeBranch;
            actualParents.add(preMergeBranch.getLastCommitBeforeMerge());
        }

        if (!actualParents.equals(expectedParents)) {
            throw new IllegalArgumentException("Actual and expected parents don't match: exptected=" + expectedParents + " got=" + actualParents);
        }

        mergeBranch.merge(preMergeBranches, getChangesOfCurrentCommit());
    }

    private void initializeNewBranchForBranchSplit(Branch branch, Branch parentBranch) {
        branch.split(parentBranch);
    }

    private void processChangesOfCurrentCommit() {
        List<FunctionChangeRow> changes = getChangesOfCurrentCommit();
        assignChangesToFunctions(changes);
    }

    private List<FunctionChangeRow> getChangesOfCurrentCommit() {
        return changesByCommitKey[currentCommit.key];
    }

    private void assignChangesToFunctions(Collection<FunctionChangeRow> changes) {
        GroupingListMap<FunctionId, FunctionChangeRow> deletions = new GroupingListMap<>();
        for (FunctionChangeRow r : changes) {
            switch (r.modType) {
                case DEL:
                    deletions.put(r.functionId, r);
                    break;
            }
        }

        for (FunctionChangeRow r : changes) {
            boolean isModOfDeletedFunction = isModOfDeletedFunction(deletions, r);

            if (isModOfDeletedFunction) {
                LOG.debug("Skipping MOD/MOVE of deleted function: " + r);
            } else {
                assignChangeToFunction(r);
            }
        }
    }

    private boolean isModOfDeletedFunction(GroupingListMap<FunctionId, FunctionChangeRow> deletions, FunctionChangeRow r) {
        switch (r.modType) {
            case MOD:
            case MOVE:
                break;
            default:
                return false;
        }

        List<FunctionChangeRow> deletionsForSameFunction = deletions.get(r.functionId);
        if (deletionsForSameFunction == null) return false;

        for (FunctionChangeRow del : deletionsForSameFunction) {
            if (del.hunk < r.hunk) {
                return true;
            }
        }

        return false;
    }

    private int changesProcessed = 0;

    private void assignChangeToFunction(FunctionChangeRow change) {
        FunctionsInBranch functions = this.currentBranch.functions;
        functions.assignChange(change);
        changesProcessed++;
    }
}
