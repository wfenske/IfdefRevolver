package de.ovgu.ifdefrevolver.commitanalysis;

import java.util.Set;

public class FunctionHistory {
    public final FunctionId function;
    public final Set<FunctionId> functionAliases;
    /**
     * All the commits that have created this function or a previous version of it.
     */
    public final Set<String> knownAddsForFunction;

    public final Set<String> guessedAddsForFunction;

    public final Set<String> nonDeletingCommitsToFunctionAndAliases;

    FunctionHistory(FunctionId function, Set<FunctionId> functionAliases, Set<String> knownAddsForFunction, Set<String> guessedAddsForFunction, Set<String> nonDeletingCommitsToFunctionAndAliases) {
        this.function = function;
        this.functionAliases = functionAliases;
        this.knownAddsForFunction = knownAddsForFunction;
        this.guessedAddsForFunction = guessedAddsForFunction;
        this.nonDeletingCommitsToFunctionAndAliases = nonDeletingCommitsToFunctionAndAliases;
    }
}
