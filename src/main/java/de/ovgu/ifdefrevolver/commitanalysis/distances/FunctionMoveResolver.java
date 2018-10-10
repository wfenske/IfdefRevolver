package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeHunk;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.util.GroupingHashSetMap;
import de.ovgu.ifdefrevolver.util.GroupingListMap;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.rank.Max;
import org.apache.commons.math3.stat.descriptive.rank.Median;
import org.apache.commons.math3.stat.descriptive.rank.Min;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Predicate;

public class FunctionMoveResolver {
    private static final Logger LOG = Logger.getLogger(FunctionMoveResolver.class);

    private GroupingListMap<String, FunctionChangeRow> changesByCommit = new GroupingListMap<>();
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
    private GroupingHashSetMap<FunctionId, String> commitsWhereFunctionIsKnownToExitsByFunctionId = new GroupingHashSetMap<>();

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

    private Set<FunctionId> getCurrentAndOlderFunctionIds(FunctionId function, Set<String> directCommitIds) {
        Set<FunctionId> functionAliases = new HashSet<>();
        for (String commit : directCommitIds) {
            Set<FunctionId> currentAliases = getCurrentAndOlderFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<FunctionId> getCurrentAndNewerFunctionIds(FunctionId function, Set<String> directCommitIds) {
        Set<FunctionId> functionAliases = new HashSet<>();
        for (String commit : directCommitIds) {
            Set<FunctionId> currentAliases = getCurrentAndNewerFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<FunctionId> getCurrentAndOlderFunctionIds(FunctionId id, final String descendantCommit) {
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
                if (commitsDistanceDb.isDescendant(descendantCommit, r.commitId)) {
                    todo.add(oldId);
                } else {
//                        LOG.debug("Rejecting move " + r + ": not an ancestor of " + commit);
                }
            }
        }

        logAliases("getCurrentAndOlderFunctionIds", id, descendantCommit, done);

        return done;
    }

    public List<List<FunctionIdWithCommit>> computeFunctionGenealogies(Collection<FunctionIdWithCommit> ids) {
        List<Set<FunctionIdWithCommit>> rawResult = new LinkedList<>();
        GroupingListMap<FunctionId, Set<FunctionIdWithCommit>> genealogiesByFunctionId = new GroupingListMap<>();

//        for (Iterator<FunctionIdWithCommit> it = ids.iterator(); it.hasNext(); ) {
//            FunctionIdWithCommit id = it.next();
//            if (!id.functionId.signature.equals("char * mdb_strerror(int err)")) {
//                it.remove();
//            }
//        }

        final int total = ids.size();
        int done = 0, lastPercentage = 0;
        for (FunctionIdWithCommit id : ids) {
            Optional<Set<FunctionIdWithCommit>> genealogyToMerge = Optional.empty();

            Set<FunctionIdWithCommit> currentAndNewerIds = getCurrentAndNewerFunctionIdsWithCommits1(id);

            for (FunctionIdWithCommit currentAndNewId : currentAndNewerIds) {
                genealogyToMerge = findMatchingGenealogyByFunctionId(currentAndNewId, genealogiesByFunctionId);
                if (genealogyToMerge.isPresent()) break;
            }

            final Set<FunctionIdWithCommit> genealogy;

            if (genealogyToMerge.isPresent()) {
                genealogy = genealogyToMerge.get();
                genealogy.addAll(currentAndNewerIds);
            } else {
                genealogy = currentAndNewerIds;
                rawResult.add(genealogy);
            }

            for (FunctionIdWithCommit currentAndNewId : currentAndNewerIds) {
                final FunctionId fid = currentAndNewId.functionId;
                List<Set<FunctionIdWithCommit>> genealogies = genealogiesByFunctionId.get(fid);
                if ((genealogies == null) || !genealogies.contains(genealogy)) {
                    genealogiesByFunctionId.put(currentAndNewId.functionId, genealogy);
                }
            }

            done++;
            int newPercentage = (int) Math.floor(done * 100.0 / total);
            if (newPercentage > lastPercentage) {
                if (newPercentage < 100 || (done == total)) {
                    LOG.info("Computed " + done + "/" + total + " genealogies (" + newPercentage + "%).");
                    lastPercentage = newPercentage;
                }
            }
        }

        Set<Map.Entry<FunctionId, List<Set<FunctionIdWithCommit>>>> entries = genealogiesByFunctionId.getMap().entrySet();
        final int uniqueFunctionIds = entries.size();
        int numberOfUnambiguousGenealogies = 0;
        int numberOfAmbiguousGenealogies = 0;
        double[] numberOfGenealogiesPerFunctionId = new double[uniqueFunctionIds];
        int ixGenealogySize = 0;

        for (Map.Entry<FunctionId, List<Set<FunctionIdWithCommit>>> e : entries) {
            FunctionId functionId = e.getKey();
            List<Set<FunctionIdWithCommit>> genealogies = e.getValue();
            int numberOfGenealogiesForFunctionId = genealogies.size();
            numberOfGenealogiesPerFunctionId[ixGenealogySize++] = numberOfGenealogiesForFunctionId;
            if (numberOfGenealogiesForFunctionId == 1) {
                numberOfUnambiguousGenealogies++;
            } else {
                numberOfAmbiguousGenealogies++;
            }
        }

        int minSize = (int) new Min().evaluate(numberOfGenealogiesPerFunctionId);
        int maxSize = (int) new Max().evaluate(numberOfGenealogiesPerFunctionId);
        double meanSize = new Mean().evaluate(numberOfGenealogiesPerFunctionId);
        double medianSize = new Median().evaluate(numberOfGenealogiesPerFunctionId);

        LOG.info("Number of unique genealogies: " + rawResult.size());
        LOG.info("Number of unique function IDs: " + uniqueFunctionIds);
        LOG.info("min/max/mean/median number of genealogies per function ID: " + minSize + "/" + maxSize + "/" + meanSize + "/" + medianSize);
        LOG.info("Number of function IDs where number of genealogies =1/>1: " + numberOfUnambiguousGenealogies + "/" + numberOfAmbiguousGenealogies);

        List<List<FunctionIdWithCommit>> result = sortGenealogies(rawResult);
        return result;
    }

    private Optional<Set<FunctionIdWithCommit>> findMatchingGenealogyByFunctionId(FunctionIdWithCommit id, GroupingListMap<FunctionId, Set<FunctionIdWithCommit>> genealogiesByFunctionId) {
        final FunctionId currentFunctionId = id.functionId;
        final String currentCommit = id.commit;
        List<Set<FunctionIdWithCommit>> possiblyMatchingGenealogies = genealogiesByFunctionId.get(currentFunctionId);
        if (possiblyMatchingGenealogies == null) {
            return Optional.empty();
        }

        for (Set<FunctionIdWithCommit> possiblyMatchingGenealogy : possiblyMatchingGenealogies) {
            for (FunctionIdWithCommit other : possiblyMatchingGenealogy) {
                FunctionId otherFunctionId = other.functionId;
                if (otherFunctionId.equals(currentFunctionId)) {
                    if (commitsDistanceDb.areCommitsRelated(currentCommit, other.commit)) {
                        return Optional.of(possiblyMatchingGenealogy);
                    } else {
                        //if (currentFunctionId.signature.equals("static int isvalidgroupname(struct berval * name)")) {
                        //LOG.info("Genealogies don't match. FunctionId=" + currentFunctionId + " but commits are unrelated: " + currentCommit + " vs. " + other.commit);
                        //AddChangeDistances.reportFunctionGenealogy(0, possiblyMatchingGenealogy);
                        //}
                    }
                }
            }
        }

        return Optional.empty();
    }

    private List<List<FunctionIdWithCommit>> sortGenealogies(List<Set<FunctionIdWithCommit>> rawResult) {
        final int total = rawResult.size();
        LOG.info("Sorting " + total + " genealogies.");

        List<List<FunctionIdWithCommit>> result = new ArrayList<>();

        int lastPercentage = 0, done = 0;

        for (Set<FunctionIdWithCommit> genealogy : rawResult) {
            List<FunctionIdWithCommit> sorted = sortGenealogy(genealogy);
            result.add(sorted);
            done++;
            int newPercentage = (int) Math.floor(done * 100.0 / total);
            if (newPercentage > lastPercentage) {
                if (newPercentage < 100 || (done == total)) {
                    LOG.info("Sorted " + done + "/" + total + " genealogies (" + newPercentage + "%).");
                    lastPercentage = newPercentage;
                }
            }
        }

        return result;
    }

    private List<FunctionIdWithCommit> sortGenealogy(Set<FunctionIdWithCommit> unsortedGenealogy) {
        List<FunctionIdWithCommit> sorted = new ArrayList<>();
        LinkedList<FunctionIdWithCommit> in = new LinkedList<>(unsortedGenealogy);

        class SortableFunctionIdWithCommit implements Comparable<SortableFunctionIdWithCommit> {
            final FunctionIdWithCommit id;
            int numberOfAncestors = 0;

            SortableFunctionIdWithCommit(FunctionIdWithCommit id) {
                this.id = id;
            }

            @Override
            public int compareTo(SortableFunctionIdWithCommit other) {
                return this.numberOfAncestors - other.numberOfAncestors;
            }
        }

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

            String[] allCommits = new String[branch.size()];
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

            for (SortableFunctionIdWithCommit current : branch) {
                sorted.add(current.id);
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

                for (FunctionChangeRow r : moves) {
                    final FunctionIdWithCommit fidWithCommit = new FunctionIdWithCommit(r.newFunctionId.get(), r.commitId, true);
                    if (done.contains(fidWithCommit) || todo.contains(fidWithCommit)) continue;
                    for (FunctionIdWithCommit ancestorFid : done) {
                        final String ancestorCommit = ancestorFid.commit;
                        if (commitsDistanceDb.isDescendant(r.commitId, ancestorCommit)) {
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

    private Set<FunctionId> getCurrentAndNewerFunctionIds(FunctionId id, final String ancestorCommit) {
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
                if (commitsDistanceDb.isDescendant(r.commitId, ancestorCommit)) {
                    todo.add(newId);
                } else {
//                        LOG.debug("Rejecting move " + r + ": not a descendant of " + ancestorCommit);
                }
            }
        }

        logAliases("getCurrentAndNewerFunctionIds", id, ancestorCommit, done);

        return done;
    }

    private void logAliases(String logFunName, FunctionId id, String commit, Set<FunctionId> result) {
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
        changesByCommit.put(change.commitId, change);
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

    private static Set<String> commitIdsFromChanges(Collection<FunctionChangeRow> changes) {
        Set<String> commitIds = new LinkedHashSet<>();
        changes.forEach((c) -> commitIds.add(c.commitId));
        return commitIds;
    }

    public FunctionHistory getFunctionHistory(final FunctionId function,
                                              final Set<String> directCommitsToThisFunction) {
        final Set<FunctionId> functionAliases = this.getCurrentAndOlderFunctionIds(function, directCommitsToThisFunction);
        // All the commits that have created this function or a previous version of it.
        final Set<FunctionChangeRow> knownAddsForFunction = this.getAddingCommitsToFunctionIncludingAliases(functionAliases);
        final Set<FunctionChangeRow> nonDeletingChangesToFunctionAndAliases =
                this.getNonDeletingCommitsToFunctionIncludingAliases(functionAliases);
        final Set<String> knownAddingCommitsForFunction = commitIdsFromChanges(knownAddsForFunction);
        final Set<String> nonDeletingCommitsToFunctionAndAliases = commitIdsFromChanges(nonDeletingChangesToFunctionAndAliases);

        final Set<String> guessedAddsForFunction =
                commitsDistanceDb.filterAncestorCommits(nonDeletingCommitsToFunctionAndAliases);
        guessedAddsForFunction.removeAll(knownAddingCommitsForFunction);
        Set<String> additionalGuessedAdds = getCommitsWhereFunctionIsKnownToExist(functionAliases);
        //additionalGuessedAdds = commitsDistanceDb.filterAncestorCommits(additionalGuessedAdds);
        //additionalGuessedAdds.removeAll(guessedAddsForFunction);
        //additionalGuessedAdds.removeAll(knownAddingCommitsForFunction);

        return new FunctionHistory(function, functionAliases, knownAddingCommitsForFunction, guessedAddsForFunction,
                additionalGuessedAdds, nonDeletingCommitsToFunctionAndAliases, commitsDistanceDb);
    }

    private Set<String> getCommitsWhereFunctionIsKnownToExist(Set<FunctionId> functionAliases) {
        Set<String> result = new HashSet<>();
        for (FunctionId function : functionAliases) {
            HashSet<String> commits = commitsWhereFunctionIsKnownToExitsByFunctionId.get(function);
            if (commits != null) {
                result.addAll(commits);
            }
        }
        return result;
    }

    public FunctionFuture getFunctionFuture(final FunctionId function,
                                            final Set<String> directCommitsToThisFunction) {
        final Set<FunctionId> functionAliases = this.getCurrentAndNewerFunctionIds(function, directCommitsToThisFunction);
        final Set<FunctionChangeRow> changesToFunctionAndAliases =
                this.getAllCommitsToFunctionIncludingAliases(functionAliases);
        final Set<String> commitsToFunctionAndAliases = commitIdsFromChanges(changesToFunctionAndAliases);
        return new FunctionFuture(function, functionAliases, commitsToFunctionAndAliases, changesToFunctionAndAliases);
    }

    public void putFunctionKnownToExistAt(FunctionId functionId, String startHash) {
        commitsWhereFunctionIsKnownToExitsByFunctionId.put(functionId, startHash);
    }
}
