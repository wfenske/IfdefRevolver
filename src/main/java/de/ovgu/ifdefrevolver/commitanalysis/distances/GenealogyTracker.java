package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.AllFunctionsRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.commitanalysis.GitUtil;
import de.ovgu.skunk.detection.data.Method;
import de.ovgu.skunk.util.GroupingHashSetMap;
import de.ovgu.skunk.util.GroupingListMap;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class GenealogyTracker {
    private static Logger LOG = Logger.getLogger(GenealogyTracker.class);
    private final List<FunctionChangeRow>[] changesByCommitKey;
    private final String repoDir;

    private CommitsDistanceDb commitsDistanceDb;
    private Queue<Commit> next;
    private BitSet done;
    private Branch currentBranch;
    private Commit currentCommit;

    private int allMoveConflicts = 0;
    private int moveConflictsThatProbablyResolveMergeConflicts = 0;

    private static class FunctionInBranch {
        private final FunctionId firstId;
        private List<FunctionChangeRow> changes = new ArrayList<>();
        private Set<FunctionInBranch> sameAs = new HashSet<>();

        public FunctionInBranch(FunctionId firstId) {
            this.firstId = firstId;
        }

        public void addChange(FunctionChangeRow change) {
            changes.add(change);
        }

        public void markSameAs(FunctionInBranch function) {
            LOG.debug("Marking as the same function: " + function + " and " + this);
            this.sameAs.add(function);
            function.sameAs.add(this);
        }

        public boolean isSameAs(FunctionInBranch other) {
            return sameAs.contains(other);
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

    private class FunctionsInBranch {
        private final Branch branch;

        private Map<FunctionId, FunctionInBranch> functionsById = new HashMap<>();
        //private Map<FunctionId, FunctionInBranch> added = new HashMap<>();
        private Map<FunctionId, DeletionRecord> deleted = new HashMap<>();
        private GroupingListMap<FunctionId, FunctionId> movedInCurrentBranch = new GroupingListMap<>();
        /**
         * Where we save functions that have somehow been replaced because we parsed the changes wrong.
         */
        private Set<FunctionInBranch> overwritten = new HashSet<>();

        public FunctionsInBranch(Branch branch) {
            this.branch = branch;
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
                    FunctionInBranch newFunction = new FunctionInBranch(functionId);
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
                function = new FunctionInBranch(id);
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
            List<FunctionId> newNames = movedInCurrentBranch.get(id);
            if (newNames == null) return null;

            ListIterator<FunctionId> reverseIter = newNames.listIterator(newNames.size());
            while (reverseIter.hasPrevious()) {
                FunctionId newId = reverseIter.previous();
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
            movedInCurrentBranch.put(oldFunctionId, newFunctionId);
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

            final boolean probableConflictResolution = changeIsProbablyRelatedToConflicResolutionAfterMerge(change);
            allMoveConflicts++;
            if (probableConflictResolution) {
                moveConflictsThatProbablyResolveMergeConflicts++;
            }

            if ((existingFunction == null) && (conflictingFunction != null) && moveAlreadyHappenedInCurrentBranch(oldFunctionId, newFunctionId)) {
                LOG.warn("Ignoring MOVE " + change + " due to a prior identical MOVE in the same branch. " + this.branch);
                return;
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
                            existingFunction = new FunctionInBranch(newFunctionId);
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

        private boolean moveAlreadyHappenedInCurrentBranch(FunctionId oldFunctionId, FunctionId newFunctionId) {
            final List<FunctionId> newFunctionIds = movedInCurrentBranch.get(oldFunctionId);
            if (newFunctionIds == null) return false;
            if (newFunctionIds.contains(newFunctionId)) return true;
            return false;
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
                oldFunction = new FunctionInBranch(functionId);
                LOG.info("Deleted function did not exist.  Created new dummy function. change=" + change);
            }

            deleteFunction(functionId, oldFunction, change.commit);
        }

        public void merge(FunctionsInBranch[] parentFunctionsList, List<FunctionChangeRow> changesOfMergeCommit) {
            if (parentFunctionsList.length == 0) return;

            mergeChangesOfMergeCommits(parentFunctionsList, changesOfMergeCommit);

            final Set<FunctionId> activeDeletes = mergeDeleted(parentFunctionsList);

            GroupingHashSetMap<FunctionId, FunctionInBranch> allNonDeletedFunctionsById = new GroupingHashSetMap<>();

            for (FunctionsInBranch parentFunctions : parentFunctionsList) {
                for (Map.Entry<FunctionId, FunctionInBranch> e : parentFunctions.functionsById.entrySet()) {
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
        }

        private Set<FunctionId> mergeDeleted(FunctionsInBranch[] parentFunctions) {
            GroupingListMap<FunctionId, DeletionRecord> lastDeletionRecordsById = new GroupingListMap<>();
            for (FunctionsInBranch parent : parentFunctions) {
                for (Map.Entry<FunctionId, DeletionRecord> e : parent.deleted.entrySet()) {
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

        private void mergeChangesOfMergeCommits(FunctionsInBranch[] parentFunctions, List<FunctionChangeRow> changesOfMergeCommit) {
            for (Iterator<FunctionChangeRow> changeIt = changesOfMergeCommit.iterator(); changeIt.hasNext(); ) {
                final FunctionChangeRow change = changeIt.next();
                final FunctionId functionId = change.functionId;
                boolean merged = false;
                for (FunctionsInBranch branch : parentFunctions) {
                    switch (change.modType) {
                        case ADD:
                            if (!branch.functionsById.containsKey(functionId)) {
                                branch.assignChange(change);
                                merged = true;
                            }
                            break;
                        case DEL:
                        case MOD:
                            if (branch.functionsById.containsKey(functionId)) {
                                branch.assignChange(change);
                                merged = true;
                            }
                            break;
                        case MOVE: {
                            FunctionId newId = change.newFunctionId.get();
                            if (branch.functionsById.containsKey(functionId)) {
                                if (newId.equals(functionId) || !branch.functionsById.containsKey(newId)) {
                                    branch.assignChange(change);
                                    merged = true;
                                }
                            }
                        }
                        break;
                    }
                    if (merged) {
                        changeIt.remove();
                        break;
                    }
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
//            if (added.containsKey(id)) {
//                LOG.warn(id + " was added in " + this.branch);
//            }
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
    }

    private class Branch {
        public final Branch[] parentBranches;
        private final Commit firstCommit;
        private FunctionsInBranch functions;
        private Set<Commit> directCommits = new LinkedHashSet<>();

        public Branch(Branch[] parentBranches, Commit firstCommit) {
            this.parentBranches = parentBranches;
            this.firstCommit = firstCommit;
            this.functions = new FunctionsInBranch(this);
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

        public void merge(Branch[] parentBranches, List<FunctionChangeRow> mergeChanges) {
            FunctionsInBranch[] parentFunctions = new FunctionsInBranch[parentBranches.length];
            for (int i = 0; i < parentBranches.length; i++) {
                parentFunctions[i] = parentBranches[i].functions;
            }
            this.functions.merge(parentFunctions, mergeChanges);
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
        }

        public boolean directlyContains(Commit commit) {
            return this.directCommits.contains(commit);
        }

        public boolean isMergeBranch() {
            return this.parentBranches.length > 1;
        }
    }

    private Branch[] branchIdsByCommitKey;

    public GenealogyTracker(CommitsDistanceDb commitsDistanceDb, Map<Commit, List<AllFunctionsRow>> allFunctionsBySnapshotStartCommit, List<FunctionChangeRow>[] changesByCommitKey, String repoDir) {
        this.commitsDistanceDb = commitsDistanceDb;
        this.changesByCommitKey = changesByCommitKey;
        this.repoDir = repoDir;
    }

    public void main() {
        final int numAllCommits = commitsDistanceDb.getCommits().size();
        done = new BitSet(numAllCommits);
        branchIdsByCommitKey = new Branch[numAllCommits];

        Set<Commit> rootCommits = commitsDistanceDb.getRoots();
        next = new LinkedList<>(rootCommits);
        while (!next.isEmpty()) {
            this.currentCommit = getNextProcessableCommit();
            processCurrentCommit();
            markCurrentCommitAsDone();
        }


        if (done.cardinality() != numAllCommits) {
            throw new RuntimeException("Some unprocessed commits remain");
        }

        LOG.info("Successfully processed all " + changesProcessed + " commits.");
        maybeReportBranchStats();
        LOG.debug("Processed " + changesProcessed + " changes.");

        if (allMoveConflicts > 0) {
            int perentage = (int) Math.round((100.0 * moveConflictsThatProbablyResolveMergeConflicts) / allMoveConflicts);
            LOG.debug("Encountered " + allMoveConflicts + " MOVE conflicts of which " +
                    moveConflictsThatProbablyResolveMergeConflicts +
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
        for (Branch id : branchIdsByCommitKey) {
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
        Commit[] parents = commit.parents();
        for (Commit parent : parents) {
            if (!isCommitProcessed(parent)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCommitProcessed(Commit commit) {
        return done.get(commit.key);
    }

    private void processCurrentCommit() {
//        if (isCommitProcessed(currentCommit)) {
//            throw new RuntimeException("Processing commit " + currentCommit + " a second time.");
//        }

        this.currentBranch = ensureBranchId(currentCommit);
        LOG.debug("Processing " + currentCommit + " in " + this.currentBranch);
        this.currentBranch.addDirectCommit(currentCommit);

        if (currentBranch.getFirstCommit() == currentCommit) {
            // This commit is the start of a new branch.  Need to handle merges etc.
            startNewBranch(currentBranch);
        }
        processChangesOfCurrentCommit();

        if ((currentBranch.getFirstCommit() == currentCommit) && (currentBranch.parentBranches.length > 1)) {
            //validateComputedFunctionsAfterMerge();
        }

        Commit[] children = currentCommit.children();
        for (Commit child : children) {
            offerIfNew(child);
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

    private Set<FunctionId> getActualFunctionIdsAtCurrentCommit() {
        Map<String, List<Method>> actualFunctionsByPath = GitUtil.listFunctionsAtCurrentCommit(this.repoDir, this.currentCommit.commitHash);
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

    private void offerIfNew(Commit commit) {
        if (!isCommitProcessed(commit) && !next.contains(commit)) {
            next.offer(commit);
        }
    }

    private Branch ensureBranchId(Commit commit) {
        final Branch existingId = branchIdsByCommitKey[commit.key];
        if (existingId != null) return existingId;

        final Branch id;
        Commit[] parents = commit.parents();
        if (parents.length == 0) { // a root commit
            Branch[] parentBranches = new Branch[0];
            id = new Branch(parentBranches, commit);
            LOG.debug("Creating new root branch " + id);
        } else if (parents.length == 1) {
            Commit parent = parents[0];
            if (parent.children().length == 1) {
                id = getBranchIdOrDie(parent);
                LOG.debug("Inheriting branch for " + commit + " from parent commit " + id);
            } else {
                // Parent splits into multiple branches
                Branch parentBranch = getBranchIdOrDie(parent);
                Branch[] parentBranches = new Branch[1];
                parentBranches[0] = parentBranch;
                id = new Branch(parentBranches, commit);
                LOG.debug("Creating new branch after branch split " + id);
            }
        } else { // a merge commit
            Branch[] parentBranches = new Branch[parents.length];
            for (int i = 0; i < parents.length; i++) {
                parentBranches[i] = getBranchIdOrDie(parents[i]);
            }
            id = new Branch(parentBranches, commit);
            LOG.debug("Creating new branch after merge " + id);
        }

        branchIdsByCommitKey[commit.key] = id;
        return id;
    }

    private Branch getBranchIdOrDie(Commit commit) {
        Branch id = branchIdsByCommitKey[commit.key];
        if (id != null) return id;
        throw new IllegalArgumentException(commit + " lacks a branch id");
    }

    private void startNewBranch(Branch branch) {
        Branch[] parentBranches = branch.getParentBranches();
        switch (parentBranches.length) {
            case 0:
                createNewRootBranch(branch);
                break;
            case 1:
                createNewBranchForBranchSplit(branch, parentBranches[0]);
                break;
            default:
                createNewBranchFromMerge(branch, parentBranches);
        }
    }

    private void createNewRootBranch(Branch branch) {
        // NOTE: nothing to do
    }

    private void createNewBranchFromMerge(Branch branch, Branch[] parentBranches) {
        int numParents = parentBranches.length;
        Branch[] mergeBranches = new Branch[numParents];
        for (int i = 0; i < numParents; i++) {
            Branch[] parents = new Branch[1];
            Branch parentParent = parentBranches[i];
            parents[0] = parentParent;
            Branch mergeBranch = new Branch(parents, this.currentCommit);
            mergeBranch.split(parentParent);
            mergeBranches[i] = mergeBranch;
        }
        branch.merge(mergeBranches, getChangesOfCurrentCommit());
    }

    private void createNewBranchForBranchSplit(Branch branch, Branch parentBranch) {
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
