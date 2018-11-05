package de.ovgu.ifdefrevolver.commitanalysis;

import java.util.Collection;

public interface IAbResRow {
    FunctionId getFunctionId();

    int getLoc();

    int getLoac();

    int getLofc();

    int getNoFl();

    int getNoFcDup();

    int getNoFcNonDup();

    int getNoNest();

    int getNoNeg();

    Collection<AbResRow> getContainedRows();
}
