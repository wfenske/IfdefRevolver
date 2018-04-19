package de.ovgu.ifdefrevolver.commitanalysis;

import org.apache.log4j.Logger;

import java.util.LinkedHashSet;
import java.util.Set;

public class FunctionFuture {
    private static final Logger LOG = Logger.getLogger(FunctionFuture.class);

    /**
     * The function id that started this search
     */
    public final FunctionId function;
    /**
     * Future ids of the same function, including the original id (the value of {@link #function})
     */
    public final Set<FunctionId> currentAndNewerFunctionIds;
    /**
     * Ids of the commits that changed this function or one of its aliases in {@link #currentAndNewerFunctionIds}
     */
    public final Set<String> commitsToFunctionAndAliases;

    /**
     * All changes to this function or one of its aliases, including additions, deletions, moves
     */
    public final Set<FunctionChangeRow> changesToFunctionAndAliases;

    public FunctionFuture(FunctionId function, Set<FunctionId> currentAndNewerFunctionIds,
                          Set<String> commitsToFunctionAndAliases, Set<FunctionChangeRow> changesToFunctionAndAliases) {
        this.function = function;
        this.currentAndNewerFunctionIds = currentAndNewerFunctionIds;
        this.commitsToFunctionAndAliases = commitsToFunctionAndAliases;
        this.changesToFunctionAndAliases = changesToFunctionAndAliases;
    }

    public Set<FunctionChangeRow> getChangesFilteredByCommitIds(Set<String> commitIds) {
        Set<FunctionChangeRow> relevantChanges = new LinkedHashSet<>();
        for (FunctionChangeRow change : changesToFunctionAndAliases) {
            if (commitIds.contains(change.commitId)) {
                relevantChanges.add(change);
            }
        }
        return relevantChanges;
    }
}
