package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.util.GroupingHashSetMap;
import de.ovgu.ifdefrevolver.util.GroupingListMap;
import org.apache.log4j.Logger;

import java.util.*;

public class FunctionMoveResolver {
    private static final Logger LOG = Logger.getLogger(FunctionMoveResolver.class);

    private GroupingListMap<String, FunctionChangeRow> changesByCommit = new GroupingListMap<>();
    private GroupingListMap<FunctionId, FunctionChangeRow> changesByFunction = new GroupingListMap<>();
    private CommitsDistanceDb commitsDistanceDb;

    public FunctionMoveResolver(CommitsDistanceDb commitsDistanceDb) {
        this.commitsDistanceDb = commitsDistanceDb;
    }

    /**
     * Key is the new function id (after the rename/move/signature change). Values are the old function ids (before the MOVE event).
     */
    private GroupingHashSetMap<FunctionId, FunctionChangeRow> movesByNewFunctionId = new GroupingHashSetMap<>();

    public Set<String> getNonDeletingCommitsToFunctionIncludingAliases(Set<FunctionId> functionAliases) {
        Set<String> result = new LinkedHashSet<>();
        for (FunctionId alias : functionAliases) {
            List<FunctionChangeRow> aliasChanges = changesByFunction.get(alias);
            if (aliasChanges == null) continue;
            for (FunctionChangeRow change : aliasChanges) {
                if (change.modType != FunctionChangeHunk.ModificationType.DEL) {
                    result.add(change.commitId);
                }
            }
        }
        return result;
    }

    public Set<String> getAddingCommitsIncludingAliases(Set<FunctionId> functionAliases) {
        Set<String> result = new LinkedHashSet<>();
        for (FunctionId alias : functionAliases) {
            List<FunctionChangeRow> aliasChanges = changesByFunction.get(alias);
            if (aliasChanges == null) continue;
            for (FunctionChangeRow change : aliasChanges) {
                if (change.modType == FunctionChangeHunk.ModificationType.ADD) {
                    result.add(change.commitId);
                }
            }
        }
        return result;
    }

    private Set<FunctionId> getFunctionAliases(FunctionId function, Set<String> directCommitIds) {
        Set<FunctionId> functionAliases = new HashSet<>();
        //for (String commit : commitsDistanceDb.filterAncestorCommits(directCommitIds)) {
        for (String commit : directCommitIds) {
            Set<FunctionId> currentAliases = getAllOlderFunctionIds(function, commit);
            functionAliases.addAll(currentAliases);
        }
        return functionAliases;
    }

    private Set<FunctionId> getAllOlderFunctionIds(FunctionId id, final String commit) {
//        LOG.debug("getAllOlderFunctionIds " + id + " " + commit);
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

                final String ancestorCommit = r.commitId;
                if (commitsDistanceDb.isDescendant(commit, ancestorCommit)) {
                    todo.add(oldId);
                } else {
//                        LOG.debug("Rejecting move " + r + ": not an ancestor of " + commit);
                }
            }
        }

//        if (LOG.isDebugEnabled()) {
//            String size;
//            Set<FunctionId> aliases = new LinkedHashSet<>(done);
//            done.remove(id);
//            switch (aliases.size()) {
//                case 0:
//                    size = "no aliases";
//                    break;
//                case 1:
//                    size = "1 alias";
//                    break;
//                default:
//                    size = aliases.size() + " aliases";
//                    break;
//            }
//            LOG.debug("getAllOlderFunctionIds(" + id + ", " + commit + ") found " +
//                    size + ": " + aliases);
//        }
        return done;
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
                movesByNewFunctionId.put(change.newFunctionId.get(), change);
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
                                              final Set<String> commitsChangingThisFunction) {
        final Set<FunctionId> functionAliases = this.getFunctionAliases(function, commitsChangingThisFunction);
        // All the commits that have created this function or a previous version of it.
        final Set<String> knownAddsForFunction = this.getAddingCommitsIncludingAliases(functionAliases);
        final Set<String> nonDeletingCommitsToFunctionAndAliases =
                this.getNonDeletingCommitsToFunctionIncludingAliases(functionAliases);
        final Set<String> guessedAddsForFunction =
                commitsDistanceDb.filterAncestorCommits(nonDeletingCommitsToFunctionAndAliases);
        guessedAddsForFunction.removeAll(knownAddsForFunction);

        return new FunctionHistory(function, functionAliases, knownAddsForFunction, guessedAddsForFunction,
                nonDeletingCommitsToFunctionAndAliases);
    }
}
