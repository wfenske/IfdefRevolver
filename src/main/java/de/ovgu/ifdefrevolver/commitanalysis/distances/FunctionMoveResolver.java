package de.ovgu.ifdefrevolver.commitanalysis.distances;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeHunk;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionChangeRow;
import de.ovgu.ifdefrevolver.commitanalysis.FunctionId;
import de.ovgu.ifdefrevolver.util.GroupingHashSetMap;
import de.ovgu.ifdefrevolver.util.GroupingListMap;
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

    public List<Set<FunctionIdWithCommit>> computeFunctionGenealogies(Collection<FunctionIdWithCommit> ids) {
        List<Set<FunctionIdWithCommit>> result = new LinkedList<>();
        final int total = ids.size();
        int done = 0, lastPercentage = 0;
        for (FunctionIdWithCommit id : ids) {
            Set<FunctionIdWithCommit> currentAndNewerIds = getCurrentAndNewerFunctionIdsWithCommits1(id);
            Optional<Set<FunctionIdWithCommit>> genealogyToMerge = findFirstSetWithCommonElement(currentAndNewerIds, result);
            if (genealogyToMerge.isPresent()) {
                genealogyToMerge.get().addAll(currentAndNewerIds);
            } else {
                result.add(currentAndNewerIds);
            }
            done++;
            int newPercentage = Math.round(done * 100.0f / total);
            if (newPercentage > lastPercentage) {
                if (newPercentage < 100 || (done == total)) {
                    LOG.info("Computed " + done + "/" + total + " genealogies (" + newPercentage + "%).");
                    lastPercentage = newPercentage;
                }
            }
        }
        return result;
    }

    private static <T> Optional<Set<T>> findFirstSetWithCommonElement(Set<T> needles, Collection<Set<T>> haystacks) {
        for (T needle : needles) {
            for (Set<T> haystack : haystacks) {
                if (haystack.contains(needle)) {
                    return Optional.of(haystack);
                }
            }
        }
        return Optional.empty();
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
                    final FunctionIdWithCommit fidWithCommit = new FunctionIdWithCommit(r.newFunctionId.get(), r.commitId);
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
