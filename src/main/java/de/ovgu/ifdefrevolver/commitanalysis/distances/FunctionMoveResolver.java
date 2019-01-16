package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeHunk;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.util.ProgressMonitor;
import de.ovgu.skunk.util.GroupingHashSetMap;
import de.ovgu.skunk.util.GroupingListMap;
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

    private Set<FunctionChangeRow> getPriorCommitsToFunctionIncludingAliasesConformingTo(Set<FunctionIdWithCommit> functionAliases,
                                                                                         Predicate<FunctionChangeRow> predicate) {
        Set<FunctionChangeRow> result = new LinkedHashSet<>();
        for (FunctionIdWithCommit alias : functionAliases) {
            List<FunctionChangeRow> aliasChanges = changesByFunction.get(alias.functionId);
            if (aliasChanges == null) continue;
            for (FunctionChangeRow change : aliasChanges) {
                if (predicate.test(change) && alias.commit.isDescendantOf(change.commit)) {
                    result.add(change);
                }
            }
        }
        return result;
    }

    private Set<FunctionChangeRow> getFutureCommitsToFunctionIncludingAliasesConformingTo(Set<FunctionIdWithCommit> functionAliases,
                                                                                          Predicate<FunctionChangeRow> predicate) {
        Set<FunctionChangeRow> result = new LinkedHashSet<>();
        for (FunctionIdWithCommit alias : functionAliases) {
            List<FunctionChangeRow> aliasChanges = changesByFunction.get(alias.functionId);
            if (aliasChanges == null) continue;
            for (FunctionChangeRow change : aliasChanges) {
                if (predicate.test(change) && change.commit.isDescendantOf(alias.commit)) {
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

    private Set<FunctionChangeRow> getNonDeletingPriorCommitsToFunctionIncludingAliases(Set<FunctionIdWithCommit> functionAliases) {
        return getPriorCommitsToFunctionIncludingAliasesConformingTo(functionAliases, NON_DELETING_CHANGE);
    }

    private Set<FunctionChangeRow> getAddingCommitsToFunctionIncludingAliases(Set<FunctionIdWithCommit> functionAliases) {
        return getPriorCommitsToFunctionIncludingAliasesConformingTo(functionAliases, ADDING_CHANGE);
    }

    private Set<FunctionChangeRow> getAllFutureCommitsToFunctionIncludingAliases(Set<FunctionIdWithCommit> functionAliases) {
        return getFutureCommitsToFunctionIncludingAliasesConformingTo(functionAliases, ANY_CHANGE);
    }

    private Set<FunctionIdWithCommit> getCurrentAndOlderFunctionIds(FunctionId function, Set<Commit> directCommitIds) {
        Set<FunctionIdWithCommit> functionAliases = new HashSet<>();
        for (Commit commit : directCommitIds) {
            Set<FunctionIdWithCommit> currentAliases = getCurrentAndOlderFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<FunctionIdWithCommit> getCurrentAndNewerFunctionIds(FunctionId function, Set<Commit> directCommitIds) {
        Set<FunctionIdWithCommit> functionAliases = new HashSet<>();
        for (Commit commit : directCommitIds) {
            Set<FunctionIdWithCommit> currentAliases = getCurrentAndNewerFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<FunctionIdWithCommit> getCurrentAndOlderFunctionIds(FunctionId id, final Commit descendantCommit) {
        Queue<FunctionIdWithCommit> todo = new LinkedList<>();
        todo.add(new FunctionIdWithCommit(id, descendantCommit, false));
        Set<FunctionIdWithCommit> done = new LinkedHashSet<>();
        FunctionIdWithCommit needle;
        while ((needle = todo.poll()) != null) {
            done.add(needle);

            Set<FunctionChangeRow> moves = movesByNewFunctionId.get(needle.functionId);
            if (moves == null) {
//                LOG.debug("No moves whatsoever for " + needle);
                continue;
            }

            for (FunctionChangeRow r : moves) {
                final FunctionIdWithCommit move = new FunctionIdWithCommit(r.functionId, r.commit, true);
                if (todo.contains(move) || done.contains(move)) continue;
                if (needle.commit.isDescendantOf(move.commit)) {
                    todo.add(move);
                } else {
//                        LOG.debug("Rejecting move " + r + ": not an ancestor of " + commit);
                }
            }
        }

//        Set<FunctionId> result = new LinkedHashSet<>();
//        for (FunctionIdWithCommit idWithCommit : done) {
//            result.add(idWithCommit.functionId);
//        }
//
//        logAliases("getCurrentAndOlderFunctionIds", id, descendantCommit, result);

        return done;
    }

    private Set<FunctionIdWithCommit> getCurrentAndNewerFunctionIds(FunctionId id, final Commit ancestorCommit) {
        Queue<FunctionIdWithCommit> todo = new LinkedList<>();
        todo.add(new FunctionIdWithCommit(id, ancestorCommit, false));
        Set<FunctionIdWithCommit> done = new LinkedHashSet<>();
        FunctionIdWithCommit needle;
        while ((needle = todo.poll()) != null) {
            done.add(needle);

            Set<FunctionChangeRow> moves = movesByOldFunctionId.get(needle.functionId);
            if (moves == null) {
//                LOG.debug("No moves whatsoever for " + needle);
                continue;
            }

            for (FunctionChangeRow r : moves) {
                final FunctionIdWithCommit move = new FunctionIdWithCommit(r.newFunctionId.get(), r.commit, true);
                if (todo.contains(move) || done.contains(move)) continue;
                if (move.commit.isDescendantOf(needle.commit)) {
                    todo.add(move);
                } else {
//                        LOG.debug("Rejecting move " + r + ": not a descendant of " + ancestorCommit);
                }
            }
        }

        //logAliases("getCurrentAndNewerFunctionIds", id, ancestorCommit, done);

        return done;
    }


    private static class GenealogySet {
        Set<BrokenFunctionGenealogy> rawResult = new HashSet<>();
        GroupingHashSetMap<FunctionId, BrokenFunctionGenealogy> genealogiesByFunctionId = new GroupingHashSetMap<>();

//        public Set<BrokenFunctionGenealogy> findMatchingGenealogies(FunctionIdWithCommit id) {
//            Collection<BrokenFunctionGenealogy> possiblyMatchingGenealogies = genealogiesByFunctionId.get(id.functionId);
//            if (possiblyMatchingGenealogies == null) {
//                return Collections.emptySet();
//            }
//
//            Set<BrokenFunctionGenealogy> result = new HashSet<>();
//
//            for (BrokenFunctionGenealogy possiblyMatchingGenealogy : possiblyMatchingGenealogies) {
//                if (possiblyMatchingGenealogy.isRelatedTo(id)) {
//                    result.add(possiblyMatchingGenealogy);
//                }
//            }
//
//            return result;
//        }

        private void removeGenealogy(BrokenFunctionGenealogy genealogy) {
            for (FunctionId fId : genealogy.getUniqueFunctionIds()) {
                Collection<BrokenFunctionGenealogy> existingGenealogies = genealogiesByFunctionId.get(fId);
                if (existingGenealogies == null) continue;
                existingGenealogies.remove(genealogy);
                if (existingGenealogies.isEmpty()) {
                    genealogiesByFunctionId.getMap().remove(fId);
                }
            }

            rawResult.remove(genealogy);
        }

        private void putNewGenealogy1(BrokenFunctionGenealogy genealogy) {
            rawResult.add(genealogy);
            for (FunctionId fId : genealogy.getUniqueFunctionIds()) {
                genealogiesByFunctionId.put(fId, genealogy);
            }
        }

        public int putNewGenealogy(BrokenFunctionGenealogy later) {
            int merges = 0;
            while (true) {
                BrokenFunctionGenealogy merged = tryMergeNewGenealogy(later);
                if (merged != null) {
                    merges++;
                    later = merged;
                } else break;
            }

            if (merges == 0) {
                putNewGenealogy1(later);
            }

            return merges;
        }

        private BrokenFunctionGenealogy tryMergeNewGenealogy(BrokenFunctionGenealogy later) {
            Set<BrokenFunctionGenealogy> earlierGenealogies = this.genealogiesByFunctionId.get(later.firstId.functionId);
            if (earlierGenealogies != null) {
                for (BrokenFunctionGenealogy earlier : earlierGenealogies) {
                    if (earlier == later) continue;
                    int successorDistance = later.isSuccessor(earlier);
                    if (successorDistance == 0) {
                        return mergeGenealogies(earlier, later);
                    }
                }
            }
            return null;
        }

        private BrokenFunctionGenealogy mergeGenealogies(BrokenFunctionGenealogy earlier, BrokenFunctionGenealogy later) {
            BrokenFunctionGenealogy merged = BrokenFunctionGenealogy.merge(earlier, later);
            removeGenealogy(earlier);
            removeGenealogy(later);
            putNewGenealogy1(merged);
            return merged;
        }

        public void mergeGenealogies() {
            ProgressMonitor pm = new ProgressMonitor(rawResult.size(), 1) {
                @Override
                protected void reportIntermediateProgress() {
                    LOG.info("Merged " + this.ticksDone + "/" + this.ticksTotal + " genealogies.");
                }

                @Override
                protected void reportFinished() {
                    reportIntermediateProgress();
                }
            };

//            tryPerfectMerges(pm);
            doImperfectMerges(pm);
        }

        protected void tryPerfectMerges(ProgressMonitor pm) {
            while (true) {
                boolean merged = tryPerfectMerge(pm);
                if (!merged) break;
            }
        }

        private boolean tryPerfectMerge(ProgressMonitor pm) {
            for (BrokenFunctionGenealogy later : rawResult) {
                Set<BrokenFunctionGenealogy> earlierGenealogies = this.genealogiesByFunctionId.get(later.firstId.functionId);
                if (earlierGenealogies == null) continue;
                for (BrokenFunctionGenealogy earlier : earlierGenealogies) {
                    if (earlier == later) continue;
                    int successorDistance = later.isSuccessor(earlier);
                    if (successorDistance == 0) {
                        mergeGenealogies(earlier, later);
                        pm.increaseDone();
                        return true;
                    }
                }
            }
            return false;
        }

        private void doImperfectMerges(ProgressMonitor pm) {
            class MergePair implements Comparable<MergePair> {
                int successorDistance;
                BrokenFunctionGenealogy earlier;
                BrokenFunctionGenealogy later;

                @Override
                public int compareTo(MergePair o) {
                    return successorDistance - o.successorDistance;
                }
            }

            while (true) {
                ProgressMonitor testMonitor = new ProgressMonitor(rawResult.size()) {
                    @Override
                    protected void reportIntermediateProgress() {
                        LOG.info("Tested " + this.ticksDone + "/" + this.ticksTotal + " merges (" + this.numberOfCurrentReport + "%)");
                    }

                    @Override
                    protected void reportFinished() {
                        LOG.info("Tested all " + this.ticksTotal + " merges");

                    }
                };

                Set<BrokenFunctionGenealogy> merged = new HashSet<>();

                boolean didMerge = false;
                for (BrokenFunctionGenealogy later : new ArrayList<>(rawResult)) {
                    MergePair winnerMerge = null;

                    Set<BrokenFunctionGenealogy> earlierGenealogies = this.genealogiesByFunctionId.get(later.firstId.functionId);
                    if (earlierGenealogies == null) continue;

                    for (BrokenFunctionGenealogy earlier : earlierGenealogies) {
                        if (earlier == later) continue;
                        int successorDistance = later.isSuccessor(earlier);
                        if (successorDistance < 0) continue;

                        MergePair p = new MergePair();
                        p.earlier = earlier;
                        p.later = later;
                        p.successorDistance = successorDistance;

                        if ((winnerMerge == null) || (winnerMerge.successorDistance > successorDistance)) {
                            if (successorDistance == 0) break;
                            winnerMerge = p;
                        }
                    }

                    if ((winnerMerge != null) && !merged.contains(winnerMerge.earlier) && !merged.contains(winnerMerge.later)) {
                        mergeGenealogies(winnerMerge.earlier, winnerMerge.later);
                        merged.add(winnerMerge.earlier);
                        merged.add(winnerMerge.later);
                        pm.increaseDone();
                        didMerge = true;
                    }

                    testMonitor.increaseDone();
                }

                if (!didMerge) break;
            }
        }
    }

    public List<List<FunctionIdWithCommit>> computeFunctionGenealogies(Collection<FunctionIdWithCommit> unsortedIds) {
        List<FunctionIdWithCommit> ids = new ArrayList<>(unsortedIds);
        Collections.sort(ids, FunctionIdWithCommit.BY_FUNCTION_ID_AND_COMMIT);
        GenealogySet result = new GenealogySet();

//        for (Iterator<FunctionIdWithCommit> it = ids.iterator(); it.hasNext(); ) {
//            FunctionIdWithCommit id = it.next();
//            if (!id.functionId.signature.equals("char * mdb_strerror(int err)")) {
//                it.remove();
//            }
//        }

        final int total = ids.size();

        int[] merges = new int[1];
        merges[0] = 0;

        ProgressMonitor pm = new ProgressMonitor(total) {
            @Override
            protected void reportIntermediateProgress() {
                LOG.info("Computed genealogies of " + ticksDone + "/" + ticksTotal +
                        " ids (" + this.numberOfCurrentReport + "%). Number of genealogies = " + result.rawResult.size() + " #merges=" + merges[0]);
            }

            @Override
            protected void reportFinished() {
                LOG.info("Done computing " + ticksTotal + " genealogies");
            }
        };

        for (FunctionIdWithCommit id : ids) {
//            if (id.functionId.signature.equals("static IDList * subtree_candidates(Backend * be, Connection * conn, Operation * op, char * base, Filter * filter, char * * attrs, int attrsonly, char * * matched, Entry * e, int * err, int lookupbase)") && id.functionId.file.equals("servers/slapd/back-ldbm/search.c") && id.commit.commitHash.equals("42e0d83cb3a1a1c5b25183f1ab74ce7edbe25de7")) {
//                LOG.info("ID is " + id);
//            }

            Set<FunctionIdWithCommit> otherIds = new LinkedHashSet<>();
            Set<FunctionIdWithCommit> currentAndNewerIds = getCurrentAndNewerFunctionIdsWithCommits1(id);
            otherIds.addAll(currentAndNewerIds);
            BrokenFunctionGenealogy currentGenealogy = new BrokenFunctionGenealogy(otherIds);
            merges[0] += result.putNewGenealogy(currentGenealogy);

//            while (true) {
//                BrokenFunctionGenealogy mergedGenealogy = result.tryMerge(currentGenealogy);
//                if (mergedGenealogy == null) break;
//                else currentGenealogy = mergedGenealogy;
//            }

            pm.increaseDone();
        }

        result.mergeGenealogies();

//        while (true) {
//            boolean merged = false;
//
//            for (BrokenFunctionGenealogy g : result.rawResult) {
//                Collection<FunctionIdWithCommit> genealogyIds = g.mergeInto(new ArrayList<>());
//
//                Set<BrokenFunctionGenealogy> genealogiesToMerge = new HashSet<>();
//                genealogiesToMerge.add(g);
//
//                for (FunctionIdWithCommit id : genealogyIds) {
//                    Set<BrokenFunctionGenealogy> matches = result.findMatchingGenealogies(id);
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

    private List<List<FunctionIdWithCommit>> sortGenealogies(Set<BrokenFunctionGenealogy> rawResult) {
        List<Set<FunctionIdWithCommit>> rawRawResult = new ArrayList<>();
        for (BrokenFunctionGenealogy g : rawResult) {
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
                    int r = FunctionIdWithCommit.BY_FUNCTION_ID_AND_COMMIT.compare(f1, f2);
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
        Collections.sort(in, FunctionIdWithCommit.BY_FUNCTION_ID_AND_COMMIT);

        class SortableFunctionIdWithCommit implements Comparable<SortableFunctionIdWithCommit> {
            final FunctionIdWithCommit id;
            int numberOfAncestors = 0;

            SortableFunctionIdWithCommit(FunctionIdWithCommit id) {
                this.id = id;
            }

            @Override
            public int compareTo(SortableFunctionIdWithCommit other) {
                int r = FunctionIdWithCommit.BY_FUNCTION_ID_AND_COMMIT.compare(this.id, other.id);
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

        FunctionIdWithCommit needle;
        while ((needle = todo.poll()) != null) {
            done.add(needle);

            Set<FunctionChangeRow> moves = movesByOldFunctionId.get(needle.functionId);
            if (moves == null) {
//                LOG.debug("No moves whatsoever for " + needle);
                continue;
            }

            FunctionIdWithCommit nextTodo = null;
            int nextTodoDistance = Integer.MAX_VALUE;
            for (FunctionChangeRow move : moves) {
                final FunctionIdWithCommit moveWithCommit = new FunctionIdWithCommit(move.newFunctionId.get(), move.commit, true);
                if (done.contains(moveWithCommit) || todo.contains(moveWithCommit)) continue;
                Optional<Integer> dist = commitsDistanceDb.minDistance(move.commit, needle.commit);
                if (!dist.isPresent()) {
                    LOG.debug("Rejecting move " + move + ": not a descendant of " + needle);
                    continue;
                }
                int distValue = dist.get();
                if (distValue < nextTodoDistance) {
                    if (nextTodo != null) {
                        LOG.debug("Replacing move " + nextTodo + " (distance=" + nextTodoDistance + ") with " +
                                moveWithCommit + " (distance=" + distValue + ")");
                    }
                    nextTodo = moveWithCommit;
                    nextTodoDistance = distValue;
                }
//                    for (FunctionIdWithCommit ancestorFid : done) {
//                        final Commit ancestorCommit = ancestorFid.commit;
//                        if (commitsDistanceDb.isDescendant(move.commit, ancestorCommit)) {
//                            todo.add(fidWithCommit);
//                            break;
//                        } else {
////                        LOG.debug("Rejecting move " + r + ": not a descendant of " + ancestorCommit);
//                        }
//                    }
            }

            if (nextTodo != null) {
                todo.add(nextTodo);
            }
        }

        //logAliases("getCurrentAndNewerFunctionIds", id, ancestorCommit, done);

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

    public Set<Commit> getCommitIdsOfChangesToThisFunction(FunctionId function) {
        final List<FunctionChangeRow> directChanges = getChangesByFunction().get(function);
        if (directChanges == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(function + " is never changed.");
            }
            return Collections.emptySet();
        }

        final Set<Commit> allDirectCommitIds = new LinkedHashSet<>(directChanges.size());
        for (FunctionChangeRow r : directChanges) {
            allDirectCommitIds.add(r.commit);
        }
        return allDirectCommitIds;
    }

    public FunctionHistory getFunctionHistory(final FunctionId function, final Commit beforeCommit) {
//        final Set<Commit> directCommitsToThisFunction = getCommitIdsOfChangesToThisFunction(function);
        final Set<FunctionIdWithCommit> functionAliases = this.getCurrentAndOlderFunctionIds(function, Collections.singleton(beforeCommit));
        // All the commits that have created this function or a previous version of it.
        final Set<FunctionChangeRow> knownAddsForFunction = this.getAddingCommitsToFunctionIncludingAliases(functionAliases);
        final Set<FunctionChangeRow> nonDeletingChangesToFunctionAndAliases =
                this.getNonDeletingPriorCommitsToFunctionIncludingAliases(functionAliases);
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

    private Set<Commit> getCommitsWhereFunctionIsKnownToExist(Set<FunctionIdWithCommit> functionAliases) {
        Set<Commit> result = new HashSet<>();
        for (FunctionIdWithCommit alias : functionAliases) {
            Set<Commit> commits = commitsWhereFunctionIsKnownToExitsByFunctionId.get(alias.functionId);
            if (commits != null) {
                for (Commit c : commits) {
                    if (alias.commit.isDescendantOf(c)) {
                        result.add(c);
                    }
                }
            }
        }
        return result;
    }

    public FunctionFuture getFunctionFuture(final FunctionId function,
                                            final Set<Commit> directCommitsToThisFunction) {
        final Set<FunctionIdWithCommit> functionAliases = this.getCurrentAndNewerFunctionIds(function, directCommitsToThisFunction);
        final Set<FunctionChangeRow> changesToFunctionAndAliases =
                this.getAllFutureCommitsToFunctionIncludingAliases(functionAliases);
        final Set<Commit> commitsToFunctionAndAliases = commitIdsFromChanges(changesToFunctionAndAliases);
        return new FunctionFuture(function, functionAliases, commitsToFunctionAndAliases, changesToFunctionAndAliases);
    }

    public void putFunctionKnownToExistAt(FunctionId functionId, Commit startCommit) {
        commitsWhereFunctionIsKnownToExitsByFunctionId.put(functionId, startCommit);
    }
}
