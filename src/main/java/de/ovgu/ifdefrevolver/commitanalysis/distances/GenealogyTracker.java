package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.AllFunctionsRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.commitanalysis.GitUtil;
import de.ovgu.ifdefrevolver.util.GroupingListMap;
import de.ovgu.skunk.detection.data.Method;
import org.apache.log4j.Logger;

import java.util.*;

public class GenealogyTracker {
    private static Logger LOG = Logger.getLogger(GenealogyTracker.class);
    private final List<FunctionChangeRow>[] changesByCommitKey;
    private final String repoDir;

    private CommitsDistanceDb commitsDistanceDb;
    private Queue<Commit> next;
    private BitSet done;
    private Branch currentBranch;
    private Commit currentCommit;

    private static class FunctionInBranch {
        private Collection<FunctionChangeRow> changes = new ArrayList<>();
        private Set<FunctionInBranch> sameAs = new HashSet<>();

        public void addChange(FunctionChangeRow change) {
            changes.add(change);
        }

        public void markSameAs(FunctionInBranch function) {
            LOG.debug("Marking as the same function: " + function + " and " + this);
            Set<FunctionInBranch> allSameFunctions = new HashSet<>();
            allSameFunctions.addAll(this.sameAs);
            allSameFunctions.addAll(function.sameAs);
            allSameFunctions.add(this);
            allSameFunctions.add(function);
            for (FunctionInBranch f : allSameFunctions) {
                f.sameAs.addAll(allSameFunctions);
            }
        }

        public boolean isSameAs(FunctionInBranch other) {
            return sameAs.contains(other);
        }
    }

    private static class FunctionsInBranch {
        private final Branch branch;

        private Map<FunctionId, FunctionInBranch> functionsById = new HashMap<>();
        private Map<FunctionId, FunctionInBranch> added = new HashMap<>();
        private Map<FunctionId, FunctionInBranch> deleted = new HashMap<>();
        private Set<FunctionId> deletedInThisBranch = new HashSet<>();
        private GroupingListMap<FunctionId, FunctionId> moved = new GroupingListMap<>();
        /**
         * Where we save functions that have somehow been replaced because we parsed the changes wrong.
         */
        private Set<FunctionInBranch> overwritten = new HashSet<>();

        public FunctionsInBranch(Branch branch) {
            this.branch = branch;
        }

        public void putAdd(FunctionChangeRow change) {
            final FunctionId id = change.functionId;
            FunctionInBranch oldFunction = functionsById.get(id);
            if (oldFunction != null) {
                // This happens rather often because some functions are defined multiple time in the same file but
                // with different presence conditions.
                LOG.debug("Added function already exists: " + id);
                FunctionInBranch replacedAdd = added.put(id, oldFunction);
                if ((replacedAdd != null) && (replacedAdd != oldFunction)) {
                    LOG.warn("Overwriting a previous add for ID " + id);
                    overwritten.add(replacedAdd);
                }
            } else {
                FunctionInBranch newFunction = new FunctionInBranch();
                functionsById.put(id, newFunction);
                added.put(id, newFunction);
            }
        }

        public void putMod(FunctionChangeRow change) {
            final FunctionId id = change.functionId;
            if (deletedInThisBranch.contains(id)) {
                LOG.info("Ignoring mod to function that was deleted by a previous commit in this branch. change=" + change + " branch=" + branch);
                return;
            }

            FunctionInBranch function = functionsById.get(id);
            if (function == null) {
                function = findRenamedFunction(id);
            }

            if (function == null) {
                function = findFunctionInParentBranches(id);
                if (function != null) {
                    functionsById.put(id, function);
                    FunctionInBranch zombie = deleted.remove(id);
                    if (zombie != function) {
                        overwritten.add(zombie);
                    }
                }
            }

            if (function == null) {
                function = findDeletedFunction(id);
                // Apparently, the function is not deleted after all.
                deleted.remove(id);
            }

            if (function == null) {
                LOG.info("Modified function does not exist: " + id);
                function = new FunctionInBranch();
                functionsById.put(id, function);
                added.put(id, function);
            }
            function.addChange(change);
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
            List<FunctionId> newNames = moved.get(id);
            if (newNames == null) return null;

            ListIterator<FunctionId> reverseIter = newNames.listIterator(newNames.size());
            while (reverseIter.hasPrevious()) {
                FunctionId newId = reverseIter.previous();
                FunctionInBranch newFunction = functionsById.get(newId);
                LOG.debug("Remapping change to a renamed function: oldId=" + id + " newId=" + newId + " branch=" + branch);
                if (newFunction != null) return newFunction;
            }
            return null;
        }

        private FunctionInBranch findDeletedFunction(FunctionId id) {
            FunctionInBranch deletedFunction = deleted.get(id);
            if (deletedFunction == null) return null;
            LOG.debug("Remapping change to a deleted function: id=" + id + " branch=" + branch);
            return deletedFunction;
        }

        public void putMove(FunctionChangeRow change) {
            final FunctionId oldFunctionId = change.functionId;
            final FunctionId newFunctionId = change.newFunctionId.get();
            FunctionInBranch oldFunction = functionsById.remove(oldFunctionId);
            FunctionInBranch newFunction = functionsById.get(newFunctionId);

            if ((oldFunction != null) && (newFunction == null)) { // normal case
                continueRegularMove(change, oldFunctionId, newFunctionId, oldFunction);
            } else {
                continueBrokenMove(change, oldFunction, newFunction);
            }
            moved.put(oldFunctionId, newFunctionId);
        }

        private void continueRegularMove(FunctionChangeRow change, FunctionId oldFunctionId, FunctionId newFunctionId, FunctionInBranch oldFunction) {
            oldFunction.addChange(change);
            functionsById.put(newFunctionId, oldFunction);
            updateAddedAndDeletedAfterMove(oldFunctionId, newFunctionId);
        }

        private void continueBrokenMove(FunctionChangeRow change, FunctionInBranch oldFunction, FunctionInBranch newFunction) {
            final FunctionId oldFunctionId = change.functionId;
            final FunctionId newFunctionId = change.newFunctionId.get();

            if (oldFunction == null) {
                oldFunction = functionsById.get(newFunctionId);
                if (oldFunction != null) {
                    LOG.info("Moved function already exists under new ID " + newFunctionId);
                } else {
                    oldFunction = new FunctionInBranch();
                    LOG.info("Moved function did not exist under old or new ID " + oldFunctionId + " or " + newFunctionId);
                }
            }

            if ((newFunction != null) && (newFunction != oldFunction)) {
                LOG.info("Move conflicts with existing function for new ID " + newFunctionId);
                overwritten.add(newFunction);
            }

            continueRegularMove(change, oldFunctionId, newFunctionId, oldFunction);
        }

        private void updateAddedAndDeletedAfterMove(FunctionId oldFunctionId, FunctionId newFunctionId) {
            FunctionInBranch oldAdd = added.remove(oldFunctionId);
            FunctionInBranch replacedAdd = null;
            if (oldAdd != null) {
                replacedAdd = added.put(newFunctionId, oldAdd);
            }

            if ((replacedAdd != null) && (replacedAdd != oldAdd)) {
                LOG.info("Move conflicts with add for change " + oldFunctionId + " -> " + newFunctionId);
                overwritten.add(replacedAdd);
            }

            FunctionInBranch replacedDel = deleted.remove(newFunctionId);
            if (replacedDel != null) {
                LOG.info("Move conflicts with deletion for change " + oldFunctionId + " -> " + newFunctionId);
                overwritten.add(replacedDel);
            }
        }

        public void putDel(FunctionChangeRow change) {
            final FunctionId functionId = change.functionId;
            deletedInThisBranch.add(functionId);

            FunctionInBranch overwrittenAdd = added.remove(functionId);
            if (overwrittenAdd != null) {
                overwritten.add(overwrittenAdd);
            }

            FunctionInBranch oldFunction = functionsById.remove(functionId);
            if ((oldFunction == null) && deleted.containsKey(functionId)) {
                if (LOG.isInfoEnabled()) {
                    if (deletedInThisBranch.contains(functionId)) {
                        LOG.info("Deleted function was already deleted in this branch. change=" + change);
                    } else {
                        LOG.info("Deleted function was already deleted in a parent branch. change=" + change);
                    }
                }
                return;
            }

            if (oldFunction == null) {
                oldFunction = new FunctionInBranch();
                LOG.info("Deleted function did not exist.  Created new dummy function. change=" + change);
            }

            FunctionInBranch overwrittenDel = deleted.put(functionId, oldFunction);
            if (overwrittenDel != null) {
                overwritten.add(overwrittenDel);
            }
        }

        public void merge(FunctionsInBranch[] parentFunctions) {
            if (parentFunctions.length == 0) return;

            this.inherit(parentFunctions[0]);

            for (int i = 1; i < parentFunctions.length; i++) {
                FunctionsInBranch otherParent = parentFunctions[i];
                mergeFirstMapIntoSecond(otherParent.added, this.added, true);
                mergeFirstMapIntoSecond(otherParent.deleted, this.deleted, false);
            }

            for (FunctionId deletedId : this.deleted.keySet()) {
                this.functionsById.remove(deletedId);
                this.added.remove(deletedId);
            }
        }

        private void mergeFirstMapIntoSecond(Map<FunctionId, FunctionInBranch> sourceMap, Map<FunctionId, FunctionInBranch> targetMap, boolean add) {
            for (Map.Entry<FunctionId, FunctionInBranch> e : sourceMap.entrySet()) {
                FunctionId id = e.getKey();
                FunctionInBranch function = e.getValue();
                if (function == null) {
                    throw new NullPointerException("No mapping for id " + id);
                }
                FunctionInBranch displaced = targetMap.put(id, function);
                if ((displaced != null) && (displaced != function)) {
                    handleDisplacedFunctionDuringBranchMerge(add, function, id, displaced);
                }
            }
        }

        private void handleDisplacedFunctionDuringBranchMerge(boolean add, FunctionInBranch function, FunctionId id, FunctionInBranch displaced) {
            boolean isSameAlready = function.isSameAs(displaced);
            if (!isSameAlready && add) {
                function.markSameAs(displaced);
            }

            if (add) {
                if (isSameAlready) {
                    LOG.debug("Parent branch adds different function for same ID " + id);
                } else {
                    LOG.info("Parent branch adds different function for same ID " + id);
                }
            } else {
                if (isSameAlready) {
                    LOG.debug("Parent branch deletes different function for same ID " + id);
                } else {
                    LOG.info("Parent branch deletes different function for same ID " + id);
                }
            }
        }

        public void inherit(FunctionsInBranch parentFunctions) {
            this.functionsById.putAll(parentFunctions.functionsById);
            this.added.putAll(parentFunctions.added);
            this.deleted.putAll(parentFunctions.deleted);
        }
    }

    private static class Branch {
        public final Branch[] parentBranches;
        private final Commit firstCommit;
        private FunctionsInBranch functions;

        public Branch(Branch[] parentBranches, Commit firstCommit) {
            this.parentBranches = parentBranches;
            this.firstCommit = firstCommit;
            this.functions = new FunctionsInBranch(this);
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

        public void merge(Branch[] parentBranches) {
            FunctionsInBranch[] parentFunctions = new FunctionsInBranch[parentBranches.length];
            for (int i = 0; i < parentBranches.length; i++) {
                parentFunctions[i] = parentBranches[i].functions;
            }
            this.functions.merge(parentFunctions);
        }

        public void split(Branch parentBranch) {
            this.functions.inherit(parentBranch.functions);
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

        LOG.debug("Successfully processed all commits.");
        maybeReportBranchStats();
        LOG.debug("Processed " + changesProcessed + " changes.");
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

        LOG.debug("Processing " + currentCommit);
        this.currentBranch = ensureBranchId(currentCommit);
        if (currentBranch.getFirstCommit() == currentCommit) {
            // This commit is the start of a new branch.  Need to handle merges etc.
            startNewBranch(currentBranch);
        }
        processChangesOfCurrentCommit();

        if ((currentBranch.getFirstCommit() == currentCommit) && (currentBranch.parentBranches.length > 1)) {
            validateComputedFunctionsAfterMerge();
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

        if (functionsThatAreMissing.isEmpty() && functionsThatShouldNotExist.isEmpty()) {
            LOG.info("After merge: Computed and actual function ids match exactly. " + this.currentBranch);
            return;
        }

        LOG.warn("After merge: # actual function ids: " + actualIds.size() + " " + this.currentBranch);
        LOG.warn("After merge: # computed function ids: " + computedIds.size() + " " + this.currentBranch);
        LOG.warn("After merge: # identical function ids: " + identicalFunctions.size() + " " + this.currentBranch);
        LOG.warn("After merge: # missing function ids: " + functionsThatAreMissing.size() + " " + this.currentBranch);
        LOG.warn("After merge: # superfluous function ids: " + functionsThatShouldNotExist.size() + " " + this.currentBranch);

        if (!functionsThatAreMissing.isEmpty()) {
            List<FunctionId> missing = new ArrayList<>(functionsThatAreMissing);
            Collections.sort(missing, FunctionId.BY_FILE_AND_SIGNATURE_ID);
            for (FunctionId id : missing) {
                LOG.warn("After merge: missing: " + id + " " + this.currentBranch);
            }
        }

        if (!functionsThatShouldNotExist.isEmpty()) {
            List<FunctionId> superfluous = new ArrayList<>(functionsThatShouldNotExist);
            Collections.sort(superfluous, FunctionId.BY_FILE_AND_SIGNATURE_ID);
            for (FunctionId id : superfluous) {
                LOG.warn("After merge: superfluous: " + id + " " + this.currentBranch);
            }
        }
    }

    private Set<FunctionId> getActualFunctionIdsAtCurrentCommit() {
        Map<String, List<Method>> actualFunctionsByPath = GitUtil.listFunctionsAtCurrentCommit(this.repoDir, this.currentCommit.commitHash);
        final Set<FunctionId> actualIds = new LinkedHashSet<>();
        for (Map.Entry<String, List<Method>> functionsInPath : actualFunctionsByPath.entrySet()) {
            String path = functionsInPath.getKey();
            for (Method f : functionsInPath.getValue()) {
                FunctionId id = new FunctionId(f.functionSignatureXml, path);
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
        branch.merge(parentBranches);
    }

    private void createNewBranchForBranchSplit(Branch branch, Branch parentBranch) {
        branch.split(parentBranch);
    }

    private void processChangesOfCurrentCommit() {
        Collection<FunctionChangeRow> changes = getChangesOfCurrentCommit();
        assignChangesToFunctions(changes);
    }

    private Collection<FunctionChangeRow> getChangesOfCurrentCommit() {
        return changesByCommitKey[currentCommit.key];
    }

    private int numSkippedModsOfDeletedFunctions = 0;

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
                numSkippedModsOfDeletedFunctions++;
                LOG.debug("Skipping mod/move of deleted function: " + r);
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
        switch (change.modType) {
            case ADD:
                functions.putAdd(change);
                break;
            case MOD:
                functions.putMod(change);
                break;
            case MOVE:
                functions.putMove(change);
                break;
            case DEL:
                functions.putDel(change);
                break;
        }
        changesProcessed++;
    }
}
