package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
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

    private Set<String> getCommitsToFunctionIncludingAliasesConformingTo(Set<FunctionId> functionAliases,
                                                                         Predicate<FunctionChangeRow> predicate) {
        Set<String> result = new LinkedHashSet<>();
        for (FunctionId alias : functionAliases) {
            List<FunctionChangeRow> aliasChanges = changesByFunction.get(alias);
            if (aliasChanges == null) continue;
            for (FunctionChangeRow change : aliasChanges) {
                if (predicate.test(change)) {
                    result.add(change.commitId);
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

    private Set<String> getNonDeletingCommitsToFunctionIncludingAliases(Set<FunctionId> functionAliases) {
        return getCommitsToFunctionIncludingAliasesConformingTo(functionAliases, NON_DELETING_CHANGE);
    }

    private Set<String> getAddingCommitsToFunctionIncludingAliases(Set<FunctionId> functionAliases) {
        return getCommitsToFunctionIncludingAliasesConformingTo(functionAliases, ADDING_CHANGE);
    }

    private Set<String> getAllCommitsToFunctionIncludingAliases(Set<FunctionId> functionAliases) {
        return getCommitsToFunctionIncludingAliasesConformingTo(functionAliases, ANY_CHANGE);
    }

    private Set<FunctionId> getOlderFunctionIds(FunctionId function, Set<String> directCommitIds) {
        Set<FunctionId> functionAliases = new HashSet<>();
        for (String commit : directCommitIds) {
            Set<FunctionId> currentAliases = getOlderFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<FunctionId> getNewerFunctionIds(FunctionId function, Set<String> directCommitIds) {
        Set<FunctionId> functionAliases = new HashSet<>();
        for (String commit : directCommitIds) {
            Set<FunctionId> currentAliases = getNewerFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<FunctionId> getOlderFunctionIds(FunctionId id, final String descendantCommit) {
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

        logAliases("getOlderFunctionIds", id, descendantCommit, done);

        return done;
    }

    private Set<FunctionId> getNewerFunctionIds(FunctionId id, final String ancestorCommit) {
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

        logAliases("getNewerFunctionIds", id, ancestorCommit, done);

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

    public FunctionHistory getFunctionHistory(final FunctionId function,
                                              final Set<String> directCommitsToThisFunction) {
        final Set<FunctionId> functionAliases = this.getOlderFunctionIds(function, directCommitsToThisFunction);
        // All the commits that have created this function or a previous version of it.
        final Set<String> knownAddsForFunction = this.getAddingCommitsToFunctionIncludingAliases(functionAliases);
        final Set<String> nonDeletingCommitsToFunctionAndAliases =
                this.getNonDeletingCommitsToFunctionIncludingAliases(functionAliases);
        final Set<String> guessedAddsForFunction =
                commitsDistanceDb.filterAncestorCommits(nonDeletingCommitsToFunctionAndAliases);
        guessedAddsForFunction.removeAll(knownAddsForFunction);

        return new FunctionHistory(function, functionAliases, knownAddsForFunction, guessedAddsForFunction,
                nonDeletingCommitsToFunctionAndAliases, commitsDistanceDb);
    }

    public FunctionFuture getFunctionFuture(final FunctionId function,
                                            final Set<String> directCommitsToThisFunction) {
        final Set<FunctionId> functionAliases = this.getNewerFunctionIds(function, directCommitsToThisFunction);
        final Set<String> commitsToFunctionAndAliases =
                this.getAllCommitsToFunctionIncludingAliases(functionAliases);
        return new FunctionFuture(function, functionAliases, commitsToFunctionAndAliases);
    }
}
