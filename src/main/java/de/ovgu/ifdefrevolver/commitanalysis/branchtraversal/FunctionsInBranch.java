package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.AbResRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.skunk.util.GroupingHashSetMap;
import de.ovgu.skunk.util.GroupingListMap;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

class FunctionsInBranch {
    private static Logger LOG = Logger.getLogger(FunctionsInBranch.class);

    protected Branch branch;
    protected final MoveConflictStats moveConflictStats;
    protected final FunctionInBranchFactory functionFactory;
    private final boolean isLogDebug;

    private Map<FunctionId, FunctionInBranch> functionsById = new HashMap<>();
    private Map<FunctionId, DeletionRecord> deleted = new HashMap<>();
    private GroupingListMap<FunctionId, FunctionChangeRow> movedInCurrentBranch = new GroupingListMap<>(1);

    public FunctionsInBranch(MoveConflictStats moveConflictStats, FunctionInBranchFactory functionFactory) {
        this.moveConflictStats = moveConflictStats;
        this.functionFactory = functionFactory;
        this.isLogDebug = LOG.isDebugEnabled();
    }

    public void close() {
        this.functionsById = Collections.emptyMap();
        this.deleted = Collections.emptyMap();
        this.movedInCurrentBranch = GroupingListMap.emptyMap();
    }

    public void putAdd(FunctionChangeRow change) {
        final FunctionId functionId = change.functionId;
        if (isLogDebug) {
            LOG.debug("ADD " + functionId + " by " + change.commit.commitHash + " in " + this.branch);
        }

        FunctionInBranch oldFunction = functionsById.get(functionId);
        if (oldFunction != null) {
            if (isLogDebug) {
                LOG.debug("Added function already exists: " + functionId);
//                FunctionInBranch replacedAdd = added.put(functionId, oldFunction);
//                if ((replacedAdd != null) && (replacedAdd != oldFunction)) {
//                    LOG.warn("Overwriting a previous add for ID " + functionId);
//                    overwritten.add(replacedAdd);
//                }
            }
            markNotDeleted(functionId, oldFunction, change.commit);
            oldFunction.addChange(change);
        } else {
            FunctionInBranch revivedFunction = undeleteFunction(functionId, change.commit);
            if (revivedFunction != null) {
                functionsById.put(functionId, revivedFunction);
                //added.put(functionId, revivedFunction);
                revivedFunction.addChange(change);
            } else {
                FunctionInBranch newFunction = functionFactory.create(functionId);
                functionsById.put(functionId, newFunction);
                //added.put(functionId, newFunction);
                markNotDeleted(functionId, newFunction, change.commit);
                newFunction.addChange(change);
            }
        }
    }

    private FunctionInBranch undeleteFunction(FunctionId functionId, Commit commit) {
        DeletionRecord lastRecord = getLastDeletionRecord(functionId);
        if (lastRecord == null) return null;

        if (!lastRecord.isActive()) {
            if (isLogDebug) LOG.debug("Function was previously deleted but currently is not: " + functionId);
        } else {
            if (isRecordInCurrentBranch(lastRecord)) {
                if (isLogDebug)
                    LOG.debug("Function was deleted in current branch but is resurrected by " + commit.commitHash + " : " + functionId);
            } else {
                if (isLogDebug)
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

        if (isLogDebug) {
            LOG.debug("MOD " + id + " by " + change.commit.commitHash + " in " + this.branch);
        }

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
            if (isLogDebug) {
                LOG.debug("MOVE is actually a MOD (both ids are the same): " + change);
            }
            putMod(change);
            return;
        }

        if (isLogDebug) {
            LOG.debug("MOVE " + oldFunctionId + " -> " + newFunctionId + " by " + change.commit.commitHash + " in " + this.branch);
        }

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
            conflictingFunction.markSameAs(existingFunction);

            logMovesInParentBranches(oldFunctionId, newFunctionId);
        }

        continueRegularMove(change, oldFunctionId, newFunctionId, existingFunction);
    }

    private boolean changeIsProbablyRelatedToConflicResolutionAfterMerge(FunctionChangeRow change) {
        return this.branch.isMergeBranch() && (change.commit == this.branch.firstCommit);
    }

    private void logMovesInParentBranches(FunctionId oldFunctionId, FunctionId newFunctionId) {
        if (!isLogDebug) return;

        for (Branch parent : this.branch.getParentBranches()) {
            if (parent.functions.moveAlreadyHappenedInCurrentBranch(oldFunctionId, newFunctionId)) {
                LOG.debug("An identical MOVE already happened in parent branch " + parent);
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
        if (isLogDebug) {
            LOG.debug("DEL " + functionId + " by " + change.commit.commitHash + " in " + this.branch);
        }

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
        oldFunction.addChange(change);
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

//        for (PreMergeBranch parentBranch : parentBranches) {
//            inheritDeletedRecords(parentBranch.functions);
//        }
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

        final Set<FunctionId> allActiveDeletes = new HashSet<>();

        for (Map.Entry<FunctionId, List<DeletionRecord>> deletionEntry : lastDeletionRecordsById.getMap().entrySet()) {
            final FunctionId id = deletionEntry.getKey();
            final List<DeletionRecord> allRecords = deletionEntry.getValue();

            final List<DeletionRecord> withoutSupersededRecords = DeletionRecord.excludeSuperseded(allRecords);
            final DeletionRecord.DeletionRecordSummary summary = DeletionRecord.summarizeRecords(withoutSupersededRecords);

            logMergeDeletedSummary(allRecords, withoutSupersededRecords, summary);

            final DeletionRecord winningDeletionRecord = withoutSupersededRecords.get(withoutSupersededRecords.size() - 1);
//                final FunctionInBranch function = winningDeletionRecord.function;
            if (summary.isNeverDeleted()) {
                if (isLogDebug) {
                    LOG.debug("Function is not deleted in any parent branch: " + id + " branch=" + this.branch);
                }
//                    for (DeletionRecord r : withoutSupersededRecords) {
//                        this.deleted.put(id, r);
//                    }
                //this.markNotDeleted(id, function, this.branch.firstCommit);
                this.deleted.put(id, winningDeletionRecord);
            } else if (summary.isAlwaysDeleted()) {
                if (isLogDebug) {
                    LOG.debug("Function is deleted in all parent branches: " + id + " branch=" + this.branch);
                }
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

    private void logMergeDeletedSummary(List<DeletionRecord> allRecords, List<DeletionRecord> withoutSupersededRecords, DeletionRecord.DeletionRecordSummary summary) {
        if (isLogDebug) {
            final DeletionRecord.DeletionRecordSummary allRecordsSummary = DeletionRecord.summarizeRecords(allRecords);
            if (allRecordsSummary.isAmbiguous() && !summary.isAmbiguous()) {
                Set<DeletionRecord> supersededRecords = new LinkedHashSet<>(allRecords);
                supersededRecords.removeAll(withoutSupersededRecords);
                LOG.debug("Summary of all records is ambiguous but summary of non-superseded records is not. allRecords=" +
                        allRecords + " withoutSupersededRecords=" + withoutSupersededRecords + " supersededRecords=" + supersededRecords);
            }
        }
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
                                if (isLogDebug) {
                                    LOG.debug("Ignoring MOD to function that is deleted by another hunk in the same commit: " + change);
                                }
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
        function.markSameAs(displaced);

        if (isLogDebug) {
            if (isSameAlready) {
                LOG.debug("Parent branch adds different function for same ID " + id + " during merge of " + this.branch);
            } else {
                LOG.debug("Parent branch adds different function for same ID " + id + " during merge of " + this.branch);
            }
        }
    }

    public void inherit(FunctionsInBranch parentFunctions) {
        this.functionsById.putAll(parentFunctions.functionsById);
        this.deleted.putAll(parentFunctions.deleted);
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

    public Set<FunctionId> getCurrentlyActiveFunctionIds() {
        return new HashSet<>(this.functionsById.keySet());
    }

    public TrackingErrorStats assignJointFunctionAbSmellRows(Commit commit, List<AbResRow> jointFunctionAbSmellRows) {
        TrackingErrorStats errorStats = new TrackingErrorStats(
                extractActualFunctionIds(jointFunctionAbSmellRows),
                new HashSet<>(this.functionsById.keySet()));

        for (AbResRow row : jointFunctionAbSmellRows) {
            if (errorStats.isMissing(row.getFunctionId())) {
                createFunctionBecauseItExistsAtSnapshotStart(commit, row);
            }
            assignJointFunctionAbSmellRow(commit, row);
        }

        for (FunctionId superfluousId : errorStats.getSuperfluousFunctions()) {
            removeFunctionBecauseItDoesNotExistAtSnapshotStart(commit, superfluousId);
        }

        return errorStats;
    }

    private void removeFunctionBecauseItDoesNotExistAtSnapshotStart(Commit commit, FunctionId functionId) {
        LOG.warn("Function does not exist in all functions and/or AB smells but exists in branch tracker. Removing it now. " +
                "function=" + functionId + " commit=" + commit);
        FunctionInBranch oldFunction = functionsById.remove(functionId);
        if (oldFunction == null) {
            throw new RuntimeException("Internal error: Trying to remove a superfluous function that does not exist. functionId=" + functionId + " commit=" + commit);
        }
        deleteFunction(functionId, oldFunction, commit);
    }

    private Set<FunctionId> extractActualFunctionIds(Collection<AbResRow> jointFunctionAbSmellRows) {
        return jointFunctionAbSmellRows.stream().map(r -> r.getFunctionId()).collect(Collectors.toCollection(HashSet::new));
    }

    private void assignJointFunctionAbSmellRow(Commit commit, AbResRow jointFunctionAbSmellRow) {
        final FunctionId functionId = jointFunctionAbSmellRow.getFunctionId();
        if (isLogDebug) {
            LOG.debug("Assigning joint function with AB smell " + functionId);
        }
        FunctionInBranch function = this.functionsById.get(functionId);
        function.addJointFunctionAbSmellRow(commit, jointFunctionAbSmellRow);
    }

    private FunctionInBranch createFunctionBecauseItExistsAtSnapshotStart(final Commit commit, AbResRow jointFunctionAbSmellRow) {
        final FunctionId functionId = jointFunctionAbSmellRow.getFunctionId();
        LOG.warn("Function exists in all functions and/or AB smells but does not exist in branch tracker. Creating it now. " +
                "function=" + functionId + " commit=" + commit);

        FunctionInBranch function = undeleteFunction(functionId, commit);
        if (function == null) {
            function = findFunctionInParentBranches(functionId);
            if (function == null) {
                function = functionFactory.create(functionId);
            }
        }

        FunctionInBranch oldFunction = this.functionsById.put(functionId, function);
        if (oldFunction != null) {
            throw new RuntimeException("Internal error: Function was newly created although it existed. oldFunction=" + oldFunction + " commit=" + commit);
        }
        return function;
    }
}
