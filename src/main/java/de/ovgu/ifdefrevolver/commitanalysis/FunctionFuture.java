package de.ovgu.ifdefrevolver.commitanalysis;

import org.apache.log4j.Logger;

import java.util.Set;

public class FunctionFuture {
    private static final Logger LOG = Logger.getLogger(FunctionFuture.class);

    public final FunctionId function;
    public final Set<FunctionId> newerFunctionIds;
    public final Set<String> commitsToFunctionAndAliases;

    public FunctionFuture(FunctionId function, Set<FunctionId> newerFunctionIds,
                          Set<String> commitsToFunctionAndAliases) {
        this.function = function;
        this.newerFunctionIds = newerFunctionIds;
        this.commitsToFunctionAndAliases = commitsToFunctionAndAliases;
    }
}
