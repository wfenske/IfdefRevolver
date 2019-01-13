package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.input.ProjectInformationReader;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.AbResRow;
import de.ovgu.ifdefrevolver.commitanalysis.AllFunctionsRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.commitanalysis.distances.AddChangeDistancesConfig;
import de.ovgu.ifdefrevolver.util.ProgressMonitor;
import de.ovgu.skunk.util.GroupingListMap;
import de.ovgu.skunk.util.LinkedGroupingListMap;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class GenealogyTracker {
    private static Logger LOG = Logger.getLogger(GenealogyTracker.class);

    private static final FunctionChangeRow[] NO_CHANGES = new FunctionChangeRow[0];

    private final FunctionChangeRow[][] changesByCommitKey;
    private final AddChangeDistancesConfig config;
    private final ProjectInformationReader projectInfo;
    private final Map<Date, List<AllFunctionsRow>> allFunctionsInSnapshots;
    private final Map<Date, List<AbResRow>> annotationDataInSnapshots;

    private List<Snapshot> snapshots;
    private List<Commit> commitsInSnapshots;
    private Branch[] branchesByCommitKey;
    private Commit currentCommit;
    private Branch currentBranch;
    private FunctionInBranchFactory functionFactory;
    private MoveConflictStats moveConflictStats;
    private int changesProcessed;
    private Map<Commit, Snapshot> snapshotsByStartCommit;
    private List<TrackingErrorStats> trackingStats;
    private BitSet processedCommits;
    private final boolean isLogDebug;
    private int numBranchesCreated;
    private int lastTimeBranchesWereClosed;

    public GenealogyTracker(ProjectInformationReader projectInfo, AddChangeDistancesConfig config, List<FunctionChangeRow>[] changesByCommitKey, Map<Date, List<AllFunctionsRow>> allFunctionsInSnapshots, Map<Date, List<AbResRow>> annotationDataInSnapshots) {
        this.changesByCommitKey = transformChangesByCommitKey(changesByCommitKey);
        this.config = config;
        this.projectInfo = projectInfo;
        this.allFunctionsInSnapshots = allFunctionsInSnapshots;
        this.annotationDataInSnapshots = annotationDataInSnapshots;
        this.isLogDebug = LOG.isDebugEnabled();
    }

    private static FunctionChangeRow[][] transformChangesByCommitKey(List<FunctionChangeRow>[] changesByCommitKey) {
        final int numCommits = changesByCommitKey.length;
        FunctionChangeRow[][] result = new FunctionChangeRow[numCommits][];
        for (int iCommit = 0; iCommit < numCommits; iCommit++) {
            result[iCommit] = toArray(changesByCommitKey[iCommit]);
        }
        return result;
    }

    private static FunctionChangeRow[] toArray(List<FunctionChangeRow> rows) {
        if (rows.isEmpty()) return NO_CHANGES;
        else {
            return rows.toArray(new FunctionChangeRow[rows.size()]);
        }
    }

    public LinkedGroupingListMap<Snapshot, FunctionGenealogy> processCommits() {
        this.processedCommits = new BitSet();
        this.numBranchesCreated = 0;
        this.lastTimeBranchesWereClosed = 0;
        this.snapshots = projectInfo.getAllSnapshots();
        this.trackingStats = new ArrayList<>();

        this.snapshotsByStartCommit = new LinkedHashMap<>();
        this.commitsInSnapshots = new ArrayList<>();
        for (Snapshot s : snapshots) {
            commitsInSnapshots.addAll(s.getCommits());
            snapshotsByStartCommit.put(s.getStartCommit(), s);
        }

        this.moveConflictStats = new MoveConflictStats();
        final int numCommits = projectInfo.commitsDb().getNumCommits();
        this.branchesByCommitKey = new Branch[numCommits];
        this.functionFactory = new FunctionInBranchFactory();
        this.changesProcessed = 0;

        ProgressMonitor pm = new ProgressMonitor(numCommits) {
            @Override
            protected void reportIntermediateProgress() {
                LOG.info("Processed " + this.ticksDone + " commits (" + this.percentage() + "%).");
            }

            @Override
            protected void reportFinished() {
                LOG.info("Processed all " + this.ticksDone + " commits (" + this.percentage() + "%).");
            }
        };

        for (Commit c : commitsInSnapshots) {
            processCommit(c);
            pm.increaseDone();
        }

        onAllCommitsProcessed();

        return aggregateResult();
    }

    private LinkedGroupingListMap<Snapshot, FunctionGenealogy> aggregateResult() {
        LinkedGroupingListMap<Snapshot, FunctionGenealogy> result = new LinkedGroupingListMap<>();
        this.snapshots.forEach(s -> result.ensureMapping(s));

        int functionUid = 0;
        for (Set<FunctionInBranch> equivalentFunctions : functionFactory.getFunctionsWithSameUid()) {
            Map<Commit, AbResRow> jointFunctionAbSmellRowsByCommit = mergeJointFunctionAbSmellRows(equivalentFunctions);
            LinkedGroupingListMap<Commit, FunctionChangeRow> changesByCommit = mergeFunctionChangeRows(equivalentFunctions);

            final Map<Snapshot, AbResRow> jointFunctionAbSmellRowsBySnapshot = new LinkedHashMap<>();
            final LinkedGroupingListMap<Snapshot, FunctionChangeRow> changesBySnapshot = new LinkedGroupingListMap<>();
            for (Map.Entry<Commit, Snapshot> e : this.snapshotsByStartCommit.entrySet()) {
                final Commit startCommit = e.getKey();
                final Snapshot snapshot = e.getValue();
                final AbResRow jointFunctionAbSmellRow = jointFunctionAbSmellRowsByCommit.get(startCommit);
                final boolean functionExistsAtStartOfSnapshot = jointFunctionAbSmellRow != null;
                if (functionExistsAtStartOfSnapshot) {
                    jointFunctionAbSmellRowsBySnapshot.put(snapshot, jointFunctionAbSmellRow);
                    changesBySnapshot.ensureMapping(snapshot);
                }
                aggregateChangesByCommitIntoChangesBySnapshot(changesByCommit, snapshot, changesBySnapshot);
            }

            // Forget all the data if the function does not appear at any snapshot start.
            if (jointFunctionAbSmellRowsBySnapshot.isEmpty()) continue;

            final Set<FunctionId> functionIds = jointFunctionAbSmellRowsBySnapshot.values().stream()
                    .map(r -> r.getFunctionId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            FunctionGenealogy genealogy = new FunctionGenealogy(functionUid, functionIds, jointFunctionAbSmellRowsBySnapshot, changesBySnapshot);
            jointFunctionAbSmellRowsBySnapshot.keySet().forEach(s -> result.put(s, genealogy));

            functionUid++;
        }

        return result;
    }

    private void aggregateChangesByCommitIntoChangesBySnapshot(LinkedGroupingListMap<Commit, FunctionChangeRow> changesByCommit, Snapshot snapshot, LinkedGroupingListMap<Snapshot, FunctionChangeRow> changesBySnapshot) {
        for (Commit commitInSnapshot : snapshot.getCommits()) {
            final List<FunctionChangeRow> changesForCommit = changesByCommit.get(commitInSnapshot);
            if (changesForCommit != null) {
                for (FunctionChangeRow changeRow : changesForCommit) {
                    changesBySnapshot.put(snapshot, changeRow);
                }
            }
        }
    }

    private LinkedGroupingListMap<Commit, FunctionChangeRow> mergeFunctionChangeRows(Set<FunctionInBranch> equivalentFunctions) {
        GroupingListMap<Commit, FunctionChangeRow> unorderedChanges = new GroupingListMap<>();

        for (FunctionInBranch equivalentFunction : equivalentFunctions) {
            Optional<LinkedGroupingListMap<Commit, FunctionChangeRow>> optChangesToEquivalentFunction = equivalentFunction.getChanges();
            if (!optChangesToEquivalentFunction.isPresent()) continue;

            for (Map.Entry<Commit, List<FunctionChangeRow>> e : optChangesToEquivalentFunction.get().getMap().entrySet()) {
                final Commit commit = e.getKey();
                for (FunctionChangeRow changeRow : e.getValue()) {
                    unorderedChanges.put(commit, changeRow);
                }
            }
        }

        return orderByCommitTraversalOrder(unorderedChanges);
    }

    private <TValue> LinkedGroupingListMap<Commit, TValue> orderByCommitTraversalOrder(GroupingListMap<Commit, TValue> unorderedMappings) {
        final LinkedGroupingListMap<Commit, TValue> result = new LinkedGroupingListMap<>();
        final Map<Commit, List<TValue>> rawResultMap = result.getMap();
        final Map<Commit, List<TValue>> rawUnorderedMappings = unorderedMappings.getMap();
        for (Commit c : this.commitsInSnapshots) {
            final List<TValue> values = rawUnorderedMappings.get(c);
            if (values != null) {
                rawResultMap.put(c, values);
            }
        }

        return result;
    }

    private <TValue> LinkedHashMap<Commit, TValue> orderByCommitTraversalOrder(Map<Commit, TValue> unorderedMappings) {
        final LinkedHashMap<Commit, TValue> result = new LinkedHashMap<>();
        for (Commit c : this.commitsInSnapshots) {
            if (unorderedMappings.containsKey(c)) {
                TValue value = unorderedMappings.get(c);
                result.put(c, value);
            }
        }

        return result;
    }

    private LinkedHashMap<Commit, AbResRow> mergeJointFunctionAbSmellRows(Set<FunctionInBranch> equivalentFunctions) {
        Map<Commit, AbResRow> rawResult = new HashMap<>();
        for (FunctionInBranch equivalentFunction : equivalentFunctions) {
            Optional<Map<Commit, AbResRow>> jointFunctionAbSmellRows = equivalentFunction.getJointFunctionAbSmellRows();
            if (jointFunctionAbSmellRows.isPresent()) {
                rawResult.putAll(jointFunctionAbSmellRows.get());
            }
        }
        return orderByCommitTraversalOrder(rawResult);
    }

    protected void processCommit(Commit c) {
        if (isLogDebug) LOG.debug("Processing " + c);
        this.currentCommit = c;
        this.currentBranch = assignBranch(currentCommit);
        if (isLogDebug) {
            LOG.debug("Current branch of " + currentCommit + " is " + this.currentBranch);
        }

        processChangesOfCurrentCommit();

        if (isCurrentCommitSnapshotStart()) {
            assignJointFunctionAbSmellRowsForCurrentCommit();
        }

        if (config.isValidateAfterMerge() && (currentBranch.getFirstCommit() == currentCommit) && (currentCommit.isMerge())) {
            validateComputedFunctionsAfterMerge();
        }

        for (Commit child : currentCommit.children()) {
            if (child.isMerge()) {
                this.currentBranch.createPreMergeBranch(child);
            }
        }

        this.processedCommits.set(c.key);
        maybeCloseObsoleteBranches();
    }

    private void maybeCloseObsoleteBranches() {
        if (((this.numBranchesCreated % 100) == 0) &&
                (this.lastTimeBranchesWereClosed != this.numBranchesCreated)) {
            this.closeObsoleteBranches();
            this.lastTimeBranchesWereClosed = this.numBranchesCreated;
        }
    }

    private void closeObsoleteBranches() {
        Set<Branch> allBranches = new HashSet<>();
        Set<Branch> activeBranches = new HashSet<>();
        for (Branch b : branchesByCommitKey) {
            if (b == null) continue;
            boolean isNew = allBranches.add(b);
            if (isNew) {
                if (isBranchActive(b)) {
                    activeBranches.add(b);
                }
            }
        }

        Set<Branch> branchesToDiscard = new HashSet<>(allBranches);
        for (Branch activeBranch : activeBranches) {
            branchesToDiscard.remove(activeBranch);
            for (Branch parent : activeBranch.parentBranches) {
                branchesToDiscard.remove(parent);
            }
        }

        LOG.info("Closing " + branchesToDiscard.size() + " obsolete branch(es).");
        for (Branch b : branchesToDiscard) {
            b.close();
        }

        final int len = branchesByCommitKey.length;
        for (int i = 0; i < len; i++) {
            Branch b = branchesByCommitKey[i];
            if (b == null) return;
            if (branchesToDiscard.contains(b)) {
                branchesByCommitKey[i] = null;
            }
        }

        final int numActiveBranches = allBranches.size() - branchesToDiscard.size();
        LOG.info(numActiveBranches + " active branches remain.");
    }

    private boolean isBranchActive(Branch b) {
        Commit c = b.getMostRecentCommit();
        for (Commit child : c.children()) {
            if (!isProcessedCommit(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProcessedCommit(Commit commit) {
        return processedCommits.get(commit.key);
    }

    private boolean isCurrentCommitSnapshotStart() {
        return snapshotsByStartCommit.containsKey(this.currentCommit);
    }

    private void assignJointFunctionAbSmellRowsForCurrentCommit() {
        List<AbResRow> jointFunctionAbSmellRows = joinAllFunctionWithAbSmellRows(this.currentCommit);
        TrackingErrorStats stats = this.currentBranch.assignJointFunctionAbSmellRows(this.currentCommit, jointFunctionAbSmellRows);
        LOG.info(stats);
        this.trackingStats.add(stats);
    }

    private List<AbResRow> joinAllFunctionWithAbSmellRows(final Commit currentCommit) {
        Snapshot snapshot = snapshotsByStartCommit.get(currentCommit);
        final Date snapshotStartDate = snapshot.getStartDate();
        List<AbResRow> abResRows = annotationDataInSnapshots.get(snapshotStartDate);
        Map<FunctionId, AbResRow> abResByFunctionId = new HashMap<>();
        for (AbResRow abResRow : abResRows) {
            abResByFunctionId.put(abResRow.getFunctionId(), abResRow);
        }

        for (AllFunctionsRow allFunctionsRow : allFunctionsInSnapshots.get(snapshotStartDate)) {
            final FunctionId functionId = allFunctionsRow.functionId;
            if (!abResByFunctionId.containsKey(functionId)) {
                AbResRow dummy = AbResRow.dummyRow(functionId, allFunctionsRow.loc);
                abResByFunctionId.put(functionId, dummy);
            }
        }

        return new ArrayList<>(abResByFunctionId.values());
    }

    protected void onAllCommitsProcessed() {
        LOG.info("Successfully processed " + commitsInSnapshots.size() + " commits and " + changesProcessed + " changes.");
        maybeReportBranchStats();
        reportMoveConflicts();
        reportTrackingStats();
    }

    private void reportTrackingStats() {
        if (!LOG.isInfoEnabled()) return;
        TrackingErrorStats totalStats = new TrackingErrorStats(this.trackingStats);
        LOG.info("Aggregated tracking accuracy over all snapshots: " + totalStats);
    }

    private void reportMoveConflicts() {
        if (moveConflictStats.allMoveConflicts > 0) {
            int perentage = (int) Math.round((100.0 * moveConflictStats.moveConflictsThatProbablyResolveMergeConflicts) / moveConflictStats.allMoveConflicts);
            LOG.info("Encountered " + moveConflictStats.allMoveConflicts + " MOVE conflicts of which " +
                    moveConflictStats.moveConflictsThatProbablyResolveMergeConflicts +
                    " (" + perentage + "%) probably resolved a merge conflict.");
        } else {
            LOG.info("Encountered no MOVE conflicts.");
        }
    }

    protected void maybeReportBranchStats() {
        if (!LOG.isInfoEnabled()) return;

        Set<Branch> branches = new HashSet<>();
        int merges = 0;
        int splits = 0;
        int roots = 0;
        for (Branch id : branchesByCommitKey) {
            if (id == null) continue;
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

        LOG.info("Created " + branches.size() + " branches. merges=" + merges + ", splits=" + splits + ", roots=" + roots);
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

    private void validateComputedFunctionsAfterMerge() {
        final Set<FunctionId> actualIds = getActualFunctionIdsAtCurrentCommit();
        final Set<FunctionId> computedIds = this.currentBranch.getCurrentlyActiveFunctionIds();

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
        final boolean logDebug = LOG.isDebugEnabled();
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

    private Branch assignBranch(Commit commit) {
        final Branch existingBranch = branchesByCommitKey[commit.key];
        if (existingBranch != null) {
            throw new IllegalArgumentException("Commit " +
                    commit + " already has a branch. Is it processed a second time?");
        }

        final Branch branch;
        Commit[] parents = commit.parents();
        if (parents.length == 0) { // a root commit
            if (isLogDebug) {
                LOG.debug("Creating new root branch for commit " + commit);
            }
            branch = Branch.createRootBranch(commit, moveConflictStats, functionFactory);
            this.numBranchesCreated++;
        } else if (parents.length == 1) {
            Commit parent = parents[0];
            if (parent.children().length == 1) {
                if (isLogDebug) {
                    LOG.debug("Inheriting branch for " + commit + " from parent " + parent);
                }
                branch = getBranchForCommitOrDie(parent);
                branch.addDirectCommit(currentCommit);
            } else {
                // Parent splits into multiple branches
                if (isLogDebug) {
                    LOG.debug("Creating new branch after branch split of " + parent + " to " + commit);
                }
                Branch parentBranch = getBranchForCommitOrDie(parent);
                branch = parentBranch.createSplitBranch(commit);
                this.numBranchesCreated++;
            }
        } else { // a merge commit
            if (isLogDebug) {
                LOG.debug("Creating new branch after merge of " + Arrays.toString(parents) + " into " + commit);
            }
            Branch[] parentBranches = new Branch[parents.length];
            for (int i = 0; i < parents.length; i++) {
                parentBranches[i] = getBranchForCommitOrDie(parents[i]);
            }

            final List<FunctionChangeRow> changesOfCurrentCommit = Arrays.asList(getChangesOfCurrentCommit());
            branch = Branch.createMergeBranch(commit, parentBranches, changesOfCurrentCommit);
            changesByCommitKey[currentCommit.key] = changesOfCurrentCommit.toArray(
                    new FunctionChangeRow[changesOfCurrentCommit.size()]);
            this.numBranchesCreated++;
        }

        branchesByCommitKey[commit.key] = branch;
        return branch;
    }

    private Branch getBranchForCommitOrDie(Commit commit) {
        Branch id = branchesByCommitKey[commit.key];
        if (id != null) return id;
        throw new IllegalArgumentException(commit + " lacks a branch");
    }

    private void processChangesOfCurrentCommit() {
        FunctionChangeRow[] changes = getChangesOfCurrentCommit();
        assignChangesToFunctions(changes);
    }

    private FunctionChangeRow[] getChangesOfCurrentCommit() {
        return changesByCommitKey[currentCommit.key];
    }

    private void assignChangesToFunctions(FunctionChangeRow[] changes) {
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
                if (isLogDebug) {
                    LOG.debug("Skipping MOD/MOVE of function that was deleted by a previous hunk in the same commit: " + r);
                }
            } else {
                assignChangeToFunctionsInBranch(r);
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
        if (deletionsForSameFunction == null) {
            return false;
        }

        for (FunctionChangeRow del : deletionsForSameFunction) {
            if (del.hunk < r.hunk) {
                return true;
            }
        }

        return false;
    }

    private void assignChangeToFunctionsInBranch(FunctionChangeRow change) {
        FunctionsInBranch functions = this.currentBranch.functions;
        functions.assignChange(change);
        changesProcessed++;
    }
}
