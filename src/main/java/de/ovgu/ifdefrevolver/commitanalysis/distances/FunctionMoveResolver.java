package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeHunk;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.util.GroupingHashSetMap;
import de.ovgu.ifdefrevolver.util.GroupingListMap;
import de.ovgu.ifdefrevolver.util.ProgressMonitor;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Predicate;

public class FunctionMoveResolver {
    private static final Logger LOG = Logger.getLogger(FunctionMoveResolver.class);

    private GroupingListMap<Commit, FunctionChangeRow> changesByCommit = new GroupingListMap<>();
    private GroupingListMap<FunctionId, FunctionChangeRow> changesByFunction = new GroupingListMap<>();
    private CommitsDistanceDb commitsDistanceDb;

    public FunctionMoveResolver(CommitsDistanceDb commitsDistanceDb) {
        this.commitsDistanceDb = commitsDistanceDb;
    }

    /**
     * Key is the new function ID (after the rename/move/signature change). Values are the MOVE changes where this
     * function ID is the new ID.
     */
    private GroupingHashSetMap<FunctionId, FunctionChangeRow> movesByNewFunctionId = new GroupingHashSetMap<>();

    /**
     * Key is the old function ID (before the rename/move/signature change). Values are the MOVE changes where this was
     * the original function ID.
     */
    private GroupingHashSetMap<FunctionId, FunctionChangeRow> movesByOldFunctionId = new GroupingHashSetMap<>();

    /**
     * Key is a function ID; Value is a set of commits for which know that the function existed at this stage.  We use
     * this information to resolve function ages in case we missed a creating commit due to an error.
     */
    private GroupingHashSetMap<FunctionId, Commit> commitsWhereFunctionIsKnownToExitsByFunctionId = new GroupingHashSetMap<>();

    private Set<FunctionChangeRow> getCommitsToFunctionIncludingAliasesConformingTo(Set<FunctionId> functionAliases,
                                                                                    Predicate<FunctionChangeRow> predicate) {
        Set<FunctionChangeRow> result = new LinkedHashSet<>();
        for (FunctionId alias : functionAliases) {
            List<FunctionChangeRow> aliasChanges = changesByFunction.get(alias);
            if (aliasChanges == null) continue;
            for (FunctionChangeRow change : aliasChanges) {
                if (predicate.test(change)) {
                    result.add(change);
                }
            }
        }
        return result;
    }

    private static final Predicate<FunctionChangeRow> NON_DELETING_CHANGE = new Predicate<FunctionChangeRow>() {
        @Override
        public boolean test(FunctionChangeRow change) {
            return (change.modType != FunctionChangeHunk.ModificationType.DEL);
        }
    };

    private static final Predicate<FunctionChangeRow> ADDING_CHANGE = new Predicate<FunctionChangeRow>() {
        @Override
        public boolean test(FunctionChangeRow change) {
            return (change.modType == FunctionChangeHunk.ModificationType.ADD);
        }
    };

    private static final Predicate<FunctionChangeRow> ANY_CHANGE = new Predicate<FunctionChangeRow>() {
        @Override
        public boolean test(FunctionChangeRow change) {
            return true;
        }
    };

    private Set<FunctionChangeRow> getNonDeletingCommitsToFunctionIncludingAliases(Set<FunctionId> functionAliases) {
        return getCommitsToFunctionIncludingAliasesConformingTo(functionAliases, NON_DELETING_CHANGE);
    }

    private Set<FunctionChangeRow> getAddingCommitsToFunctionIncludingAliases(Set<FunctionId> functionAliases) {
        return getCommitsToFunctionIncludingAliasesConformingTo(functionAliases, ADDING_CHANGE);
    }

    private Set<FunctionChangeRow> getAllCommitsToFunctionIncludingAliases(Set<FunctionId> functionAliases) {
        return getCommitsToFunctionIncludingAliasesConformingTo(functionAliases, ANY_CHANGE);
    }

    private Set<FunctionId> getCurrentAndOlderFunctionIds(FunctionId function, Set<Commit> directCommitIds) {
        Set<FunctionId> functionAliases = new HashSet<>();
        for (Commit commit : directCommitIds) {
            Set<FunctionId> currentAliases = getCurrentAndOlderFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<FunctionId> getCurrentAndNewerFunctionIds(FunctionId function, Set<Commit> directCommitIds) {
        Set<FunctionId> functionAliases = new HashSet<>();
        for (Commit commit : directCommitIds) {
            Set<FunctionId> currentAliases = getCurrentAndNewerFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<FunctionId> getCurrentAndOlderFunctionIds(FunctionId id, final Commit descendantCommit) {
        Queue<FunctionId> todo = new LinkedList<>();
        todo.add(id);
        Set<FunctionId> done = new LinkedHashSet<>();
        FunctionId needle;
        while ((needle = todo.poll()) != null) {
            done.add(needle);

            Set<FunctionChangeRow> moves = movesByNewFunctionId.get(needle);
            if (moves == null) {
//                LOG.debug("No moves whatsoever for " + needle);
                continue;
            }

            for (FunctionChangeRow r : moves) {
                final FunctionId oldId = r.functionId;
                if (todo.contains(oldId) || done.contains(oldId)) continue;
                if (commitsDistanceDb.isDescendant(descendantCommit, r.commit)) {
                    todo.add(oldId);
                } else {
//                        LOG.debug("Rejecting move " + r + ": not an ancestor of " + commit);
                }
            }
        }

        logAliases("getCurrentAndOlderFunctionIds", id, descendantCommit, done);

        return done;
    }

    private static class GenealogySet {
        Set<FunctionGenealogy> rawResult = new HashSet<>();
        GroupingHashSetMap<FunctionId, FunctionGenealogy> genealogiesByFunctionId = new GroupingHashSetMap<>();

        public Set<FunctionGenealogy> findMatchingGenealogies(FunctionIdWithCommit id) {
            Collection<FunctionGenealogy> possiblyMatchingGenealogies = genealogiesByFunctionId.get(id.functionId);
            if (possiblyMatchingGenealogies == null) {
                return Collections.emptySet();
            }

            Set<FunctionGenealogy> result = new HashSet<>();

            for (FunctionGenealogy possiblyMatchingGenealogy : possiblyMatchingGenealogies) {
                if (possiblyMatchingGenealogy.isRelatedTo(id)) {
                    result.add(possiblyMatchingGenealogy);
                }
            }

            return result;
        }

        public void removeGenealogies(Collection<FunctionGenealogy> genealogies) {
            Set<FunctionId> uniqueFunctionIds = new HashSet<>();

            for (FunctionGenealogy g : genealogies) {
                uniqueFunctionIds.addAll(g.getUniqueFunctionIds());
            }

            for (FunctionId fId : uniqueFunctionIds) {
                Collection<FunctionGenealogy> existingGenealogies = genealogiesByFunctionId.get(fId);
                if (existingGenealogies == null) continue;
                existingGenealogies.removeAll(genealogies);
                if (existingGenealogies.isEmpty()) {
                    genealogiesByFunctionId.getMap().remove(fId);
                }
            }

            rawResult.removeAll(genealogies);
        }

        public void putNewGenealogy(FunctionGenealogy genealogy) {
            rawResult.add(genealogy);

            Set<FunctionId> uniqueFunctionIds = genealogy.getUniqueFunctionIds();
            for (FunctionId fId : uniqueFunctionIds) {
                genealogiesByFunctionId.put(fId, genealogy);
            }
        }

        public void putMergegedGenealogy(Set<FunctionGenealogy> genealogiesToMerge, Set<FunctionIdWithCommit> additionalIds) {
            Set<FunctionIdWithCommit> allIds = new HashSet<>(additionalIds);
            for (FunctionGenealogy merge : genealogiesToMerge) {
                merge.mergeInto(allIds);
            }
            final FunctionGenealogy genealogy = new FunctionGenealogy(allIds);
            removeGenealogies(genealogiesToMerge);
            putNewGenealogy(genealogy);
        }

        public void putMergegedGenealogy(Set<FunctionGenealogy> genealogiesToMerge) {
            putMergegedGenealogy(genealogiesToMerge, Collections.emptySet());
        }
    }

    public List<List<FunctionIdWithCommit>> computeFunctionGenealogies(Collection<FunctionIdWithCommit> unsortedIds) {
        List<FunctionIdWithCommit> ids = new ArrayList<>(unsortedIds);
        Collections.sort(ids, FunctionIdWithCommit.BY_FUNCTION_ID_AND_COMMIT_HASH);
        GenealogySet result = new GenealogySet();

//        for (Iterator<FunctionIdWithCommit> it = ids.iterator(); it.hasNext(); ) {
//            FunctionIdWithCommit id = it.next();
//            if (!id.functionId.signature.equals("char * mdb_strerror(int err)")) {
//                it.remove();
//            }
//        }

        final int total = ids.size();

        ProgressMonitor pm = new ProgressMonitor(total) {
            @Override
            protected void reportIntermediateProgress() {
                LOG.info("Computed " + ticksDone + "/" + ticksTotal +
                        " genealogies (" + this.numberOfCurrentReport + "%)");
            }

            @Override
            protected void reportFinished() {
                LOG.info("Done " + ticksTotal + " genealogies");
            }
        };

        for (FunctionIdWithCommit id : ids) {
            Set<FunctionIdWithCommit> otherIds = new LinkedHashSet<>();
            Set<FunctionIdWithCommit> currentAndNewerIds = getCurrentAndNewerFunctionIdsWithCommits1(id);
            otherIds.addAll(currentAndNewerIds);

            Set<FunctionGenealogy> genealogiesToMerge = new HashSet<>();
            for (FunctionIdWithCommit otherId : otherIds) {
                Set<FunctionGenealogy> matches = result.findMatchingGenealogies(otherId);
                genealogiesToMerge.addAll(matches);
            }

            if (genealogiesToMerge.isEmpty()) {
                final FunctionGenealogy genealogy = new FunctionGenealogy(otherIds);
                result.putNewGenealogy(genealogy);
            } else {
                result.putMergegedGenealogy(genealogiesToMerge, otherIds);
            }

            pm.increaseDone();
        }

//        while (true) {
//            boolean merged = false;
//
//            for (FunctionGenealogy g : result.rawResult) {
//                Collection<FunctionIdWithCommit> genealogyIds = g.mergeInto(new ArrayList<>());
//
//                Set<FunctionGenealogy> genealogiesToMerge = new HashSet<>();
//                genealogiesToMerge.add(g);
//
//                for (FunctionIdWithCommit id : genealogyIds) {
//                    Set<FunctionGenealogy> matches = result.findMatchingGenealogies(id);
//                    genealogiesToMerge.addAll(matches);
//                }
//
//                if (genealogiesToMerge.size() == 1) continue;
//                result.putMergegedGenealogy(genealogiesToMerge);
//                merged = true;
//                break;
//            }
//
//            if (!merged) break;
//        }

        List<List<FunctionIdWithCommit>> sortedResult = sortGenealogies(result.rawResult);
        return sortedResult;
    }

    private List<List<FunctionIdWithCommit>> sortGenealogies(Set<FunctionGenealogy> rawResult) {
        List<Set<FunctionIdWithCommit>> rawRawResult = new ArrayList<>();
        for (FunctionGenealogy g : rawResult) {
            Set<FunctionIdWithCommit> gSet = new HashSet<>();
            g.mergeInto(gSet);
            rawRawResult.add(gSet);
        }

        return sortGenealogies(rawRawResult);
    }

    private List<List<FunctionIdWithCommit>> sortGenealogies(List<Set<FunctionIdWithCommit>> rawResult) {
        final int total = rawResult.size();
        LOG.info("Sorting " + total + " genealogies.");

        List<List<FunctionIdWithCommit>> result = new ArrayList<>();

        ProgressMonitor pm = new ProgressMonitor(total) {
            @Override
            protected void reportIntermediateProgress() {
                LOG.info("Sorted " + ticksDone + "/" + ticksTotal + " genealogies (" + numberOfCurrentReport + "%).");
            }

            @Override
            protected void reportFinished() {
                LOG.info("Done sorting " + ticksTotal + " genealogies.");
            }
        };

        for (Set<FunctionIdWithCommit> genealogy : rawResult) {
            List<FunctionIdWithCommit> sorted = sortGenealogy(genealogy);
            result.add(sorted);
            pm.increaseDone();
        }

        class GenealogyComparator implements Comparator<List<FunctionIdWithCommit>> {
            @Override
            public int compare(List<FunctionIdWithCommit> o1, List<FunctionIdWithCommit> o2) {
                int len = Math.min(o1.size(), o2.size());
                for (int i = 0; i < len; i++) {
                    FunctionIdWithCommit f1 = o1.get(i);
                    FunctionIdWithCommit f2 = o2.get(i);
                    int r = FunctionIdWithCommit.BY_FUNCTION_ID_AND_COMMIT_HASH.compare(f1, f2);
                    if (r != 0) return r;
                }
                return o2.size() - o1.size();
            }
        }

        Collections.sort(result, new GenealogyComparator());

        return result;
    }

    private List<FunctionIdWithCommit> sortGenealogy(Set<FunctionIdWithCommit> unsortedGenealogy) {
        LinkedList<FunctionIdWithCommit> in = new LinkedList<>(unsortedGenealogy);
        Collections.sort(in, FunctionIdWithCommit.BY_FUNCTION_ID_AND_COMMIT_HASH);

        class SortableFunctionIdWithCommit implements Comparable<SortableFunctionIdWithCommit> {
            final FunctionIdWithCommit id;
            int numberOfAncestors = 0;

            SortableFunctionIdWithCommit(FunctionIdWithCommit id) {
                this.id = id;
            }

            @Override
            public int compareTo(SortableFunctionIdWithCommit other) {
                int r = FunctionIdWithCommit.BY_FUNCTION_ID_AND_COMMIT_HASH.compare(this.id, other.id);
                if (r != 0) return r;
                return this.numberOfAncestors - other.numberOfAncestors;
            }
        }

        class BranchComparator implements Comparator<List<SortableFunctionIdWithCommit>> {
            @Override
            public int compare(List<SortableFunctionIdWithCommit> o1, List<SortableFunctionIdWithCommit> o2) {
                int o1Size = o1.size();
                int r = o2.size() - o1Size;
                if (r != 0) return r;
                for (int i = 0; i < o1Size; i++) {
                    r = o1.get(i).compareTo(o2.get(i));
                    if (r != 0) return r;
                }
                return 0;
            }
        }

        List<List<SortableFunctionIdWithCommit>> branches = new ArrayList<>();

        while (!in.isEmpty()) {
            final FunctionIdWithCommit first = in.removeFirst();
            List<SortableFunctionIdWithCommit> branch = new ArrayList<>();
            branch.add(new SortableFunctionIdWithCommit(first));

            for (Iterator<FunctionIdWithCommit> it = in.iterator(); it.hasNext(); ) {
                FunctionIdWithCommit next = it.next();
                if (commitsDistanceDb.areCommitsRelated(first.commit, next.commit)) {
                    branch.add(new SortableFunctionIdWithCommit(next));
                    it.remove();
                }
            }

            Commit[] allCommits = new Commit[branch.size()];
            {
                int ix = 0;
                for (SortableFunctionIdWithCommit current : branch) {
                    allCommits[ix++] = current.id.commit;
                }
            }

            for (SortableFunctionIdWithCommit current : branch) {
                current.numberOfAncestors = commitsDistanceDb.countAncestors(current.id.commit, allCommits);
            }

            Collections.sort(branch);
            branches.add(branch);
        }

        Collections.sort(branches, new BranchComparator());

        List<FunctionIdWithCommit> sorted = new ArrayList<>();
        for (List<SortableFunctionIdWithCommit> branch : branches) {
            for (SortableFunctionIdWithCommit s : branch) {
                sorted.add(s.id);
            }
        }

        return sorted;
    }

    private Set<FunctionIdWithCommit> getCurrentAndNewerFunctionIdsWithCommits1(FunctionIdWithCommit id) {
        Queue<FunctionIdWithCommit> todo = new LinkedList<>();
        todo.add(id);
        Set<FunctionIdWithCommit> done = new LinkedHashSet<>();

        while (true) {
            final int doneSizeBefore = done.size();

            FunctionIdWithCommit needle;
            while ((needle = todo.poll()) != null) {
                done.add(needle);

                Set<FunctionChangeRow> moves = movesByOldFunctionId.get(needle.functionId);
                if (moves == null) {
//                LOG.debug("No moves whatsoever for " + needle);
                    continue;
                }

                for (FunctionChangeRow move : moves) {
                    final FunctionIdWithCommit fidWithCommit = new FunctionIdWithCommit(move.newFunctionId.get(), move.commit, true);
                    if (done.contains(fidWithCommit) || todo.contains(fidWithCommit)) continue;
                    for (FunctionIdWithCommit ancestorFid : done) {
                        final Commit ancestorCommit = ancestorFid.commit;
                        if (commitsDistanceDb.isDescendant(move.commit, ancestorCommit)) {
                            todo.add(fidWithCommit);
                            break;
                        } else {
//                        LOG.debug("Rejecting move " + r + ": not a descendant of " + ancestorCommit);
                        }
                    }
                }
            }

            if (doneSizeBefore == done.size()) {
                break;
            } else {
                todo.addAll(done);
            }
        }

        //logAliases("getCurrentAndNewerFunctionIds", id, ancestorCommit, done);

        return done;
    }

    private Set<FunctionIdWithCommit> getCurrentAndOlderFunctionIdsWithCommits1(FunctionIdWithCommit id) {
        Queue<FunctionIdWithCommit> todo = new LinkedList<>();
        todo.add(id);
        Set<FunctionIdWithCommit> done = new LinkedHashSet<>();

        while (true) {
            final int doneSizeBefore = done.size();

            FunctionIdWithCommit needle;
            while ((needle = todo.poll()) != null) {
                done.add(needle);

                Set<FunctionChangeRow> moves = movesByNewFunctionId.get(needle.functionId);
                if (moves == null) {
//                LOG.debug("No moves whatsoever for " + needle);
                    continue;
                }

                for (FunctionChangeRow r : moves) {
                    final FunctionIdWithCommit fidWithCommit = new FunctionIdWithCommit(r.functionId, r.commit, false);
                    if (done.contains(fidWithCommit) || todo.contains(fidWithCommit)) continue;
                    for (FunctionIdWithCommit descendantFid : done) {
                        final Commit descendantCommit = descendantFid.commit;
                        if (commitsDistanceDb.isDescendant(descendantCommit, r.commit)) {
                            todo.add(fidWithCommit);
                            break;
                        } else {
//                        LOG.debug("Rejecting move " + r + ": not a descendant of " + ancestorCommit);
                        }
                    }
                }
            }

            if (doneSizeBefore == done.size()) {
                break;
            } else {
                todo.addAll(done);
            }
        }

        //logAliases("getCurrentAndNewerFunctionIds", id, ancestorCommit, done);

        return done;
    }

    private Set<FunctionId> getCurrentAndNewerFunctionIds(FunctionId id, final Commit ancestorCommit) {
        Queue<FunctionId> todo = new LinkedList<>();
        todo.add(id);
        Set<FunctionId> done = new LinkedHashSet<>();
        FunctionId needle;
        while ((needle = todo.poll()) != null) {
            done.add(needle);

            Set<FunctionChangeRow> moves = movesByOldFunctionId.get(needle);
            if (moves == null) {
//                LOG.debug("No moves whatsoever for " + needle);
                continue;
            }

            for (FunctionChangeRow r : moves) {
                final FunctionId newId = r.newFunctionId.get();
                if (todo.contains(newId) || done.contains(newId)) continue;
                if (commitsDistanceDb.isDescendant(r.commit, ancestorCommit)) {
                    todo.add(newId);
                } else {
//                        LOG.debug("Rejecting move " + r + ": not a descendant of " + ancestorCommit);
                }
            }
        }

        logAliases("getCurrentAndNewerFunctionIds", id, ancestorCommit, done);

        return done;
    }

    private void logAliases(String logFunName, FunctionId id, Commit commit, Set<FunctionId> result) {
        if (!LOG.isDebugEnabled()) return;

        String sizeStr;
        Set<FunctionId> aliases = new LinkedHashSet<>(result);
        result.remove(id);
        switch (aliases.size()) {
            case 0:
                sizeStr = "no aliases";
                break;
            case 1:
                sizeStr = "1 alias";
                break;
            default:
                sizeStr = aliases.size() + " aliases";
                break;
        }
        LOG.debug(logFunName + "(" + id + ", " + commit + ") found " +
                sizeStr + ": " + aliases);
    }

    public void putChange(FunctionChangeRow change) {
        changesByFunction.put(change.functionId, change);
        Optional<FunctionId> newFunctionId = change.newFunctionId;
        if (newFunctionId.isPresent()) {
            changesByFunction.put(newFunctionId.get(), change);
        }
        changesByCommit.put(change.commit, change);
    }

    public void parseRenames() {
        LOG.debug("Parsing all renames/moves/signature changes");
        int numMoves = 0;
        for (Collection<FunctionChangeRow> changes : changesByCommit.getMap().values()) {
            for (FunctionChangeRow change : changes) {
                if (change.modType != FunctionChangeHunk.ModificationType.MOVE) continue;
                numMoves++;
                if (!change.newFunctionId.isPresent()) {
                    throw new RuntimeException("Encountered a MOVE without a new function id: " + change);
                }
                final FunctionId oldFunctionId = change.functionId;
                final FunctionId newFunctionId = change.newFunctionId.get();
                movesByOldFunctionId.put(oldFunctionId, change);
                movesByNewFunctionId.put(newFunctionId, change);
            }
        }

        if (LOG.isDebugEnabled()) {
            for (Map.Entry<FunctionId, HashSet<FunctionChangeRow>> e : movesByNewFunctionId.getMap().entrySet()) {
                FunctionId functionId = e.getKey();
                HashSet<FunctionChangeRow> moves = e.getValue();
                for (FunctionChangeRow move : moves) {
                    LOG.debug(functionId + " <- " + move.functionId);
                }
            }
        }

        LOG.debug("Parsed " + numMoves + " MOVE events.");
    }

    public Map<FunctionId, List<FunctionChangeRow>> getChangesByFunction() {
        return changesByFunction.getMap();
    }

    private static Set<Commit> commitIdsFromChanges(Collection<FunctionChangeRow> changes) {
        Set<Commit> commitIds = new LinkedHashSet<>();
        changes.forEach((c) -> commitIds.add(c.commit));
        return commitIds;
    }

    public FunctionHistory getFunctionHistory(final FunctionId function,
                                              final Set<Commit> directCommitsToThisFunction) {
        final Set<FunctionId> functionAliases = this.getCurrentAndOlderFunctionIds(function, directCommitsToThisFunction);
        // All the commits that have created this function or a previous version of it.
        final Set<FunctionChangeRow> knownAddsForFunction = this.getAddingCommitsToFunctionIncludingAliases(functionAliases);
        final Set<FunctionChangeRow> nonDeletingChangesToFunctionAndAliases =
                this.getNonDeletingCommitsToFunctionIncludingAliases(functionAliases);
        final Set<Commit> knownAddingCommitsForFunction = commitIdsFromChanges(knownAddsForFunction);
        final Set<Commit> nonDeletingCommitsToFunctionAndAliases = commitIdsFromChanges(nonDeletingChangesToFunctionAndAliases);

        final Set<Commit> guessedAddsForFunction =
                commitsDistanceDb.filterAncestorCommits(nonDeletingCommitsToFunctionAndAliases);
        guessedAddsForFunction.removeAll(knownAddingCommitsForFunction);
        Set<Commit> additionalGuessedAdds = getCommitsWhereFunctionIsKnownToExist(functionAliases);
        //additionalGuessedAdds = commitsDistanceDb.filterAncestorCommits(additionalGuessedAdds);
        //additionalGuessedAdds.removeAll(guessedAddsForFunction);
        //additionalGuessedAdds.removeAll(knownAddingCommitsForFunction);

        return new FunctionHistory(function, functionAliases, knownAddingCommitsForFunction, guessedAddsForFunction,
                additionalGuessedAdds, nonDeletingCommitsToFunctionAndAliases, commitsDistanceDb);
    }

    private Set<Commit> getCommitsWhereFunctionIsKnownToExist(Set<FunctionId> functionAliases) {
        Set<Commit> result = new HashSet<>();
        for (FunctionId function : functionAliases) {
            Set<Commit> commits = commitsWhereFunctionIsKnownToExitsByFunctionId.get(function);
            if (commits != null) {
                result.addAll(commits);
            }
        }
        return result;
    }

    public FunctionFuture getFunctionFuture(final FunctionId function,
                                            final Set<Commit> directCommitsToThisFunction) {
        final Set<FunctionId> functionAliases = this.getCurrentAndNewerFunctionIds(function, directCommitsToThisFunction);
        final Set<FunctionChangeRow> changesToFunctionAndAliases =
                this.getAllCommitsToFunctionIncludingAliases(functionAliases);
        final Set<Commit> commitsToFunctionAndAliases = commitIdsFromChanges(changesToFunctionAndAliases);
        return new FunctionFuture(function, functionAliases, commitsToFunctionAndAliases, changesToFunctionAndAliases);
    }

    public void putFunctionKnownToExistAt(FunctionId functionId, Commit startCommit) {
        commitsWhereFunctionIsKnownToExitsByFunctionId.put(functionId, startCommit);
    }
}
