package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.data.Method;

import java.util.List;

abstract class DiffSideFunctionList {
    private final String path;
    private final List<Method> functionsByOccurrence;

    public DiffSideFunctionList(String path, List<Method> functionsByOccurrence) {
        this.path = path;
        this.functionsByOccurrence = functionsByOccurrence;
    }

    public String getPath() {
        return path;
    }

    public List<Method> getFunctionsByOccurrence() {
        return functionsByOccurrence;
    }

    public boolean hasAlternativeDefinitions() {
        return functionsByOccurrence.stream().anyMatch(f -> f.hasAlternativeDefinitions());
    }
}

class DiffASideFunctionList extends DiffSideFunctionList {
    public DiffASideFunctionList(String path, List<Method> functionsByOccurrence) {
        super(path, functionsByOccurrence);
    }
}

class DiffBSideFunctionList extends DiffSideFunctionList {
    public DiffBSideFunctionList(String path, List<Method> functionsByOccurrence) {
        super(path, functionsByOccurrence);
    }
}
