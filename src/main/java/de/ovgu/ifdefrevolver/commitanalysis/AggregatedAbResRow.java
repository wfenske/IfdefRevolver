package de.ovgu.ifdefrevolver.commitanalysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.ToIntFunction;

public class AggregatedAbResRow implements IAbResRow {
    private final FunctionId functionId;
    private final Collection<AbResRow> containedRows;

    public AggregatedAbResRow(IAbResRow left, IAbResRow right) {
        this.containedRows = new ArrayList<>();
        this.containedRows.addAll(left.getContainedRows());
        this.containedRows.addAll(right.getContainedRows());
        this.functionId = left.getContainedRows().iterator().next().getFunctionId();
    }

    public AggregatedAbResRow(AbResRow first, List<AbResRow> rest) {
        this.containedRows = new ArrayList<>();
        this.containedRows.add(first);
        this.containedRows.addAll(rest);
        this.functionId = first.getFunctionId();
    }

    @Override
    public FunctionId getFunctionId() {
        return functionId;
    }

    private int average(ToIntFunction<AbResRow> getAttr) {
        return (int) Math.round(containedRows.stream().mapToInt(getAttr).average().getAsDouble());
    }

    @Override
    public int getLoc() {
        return average(IAbResRow::getLoc);
    }

    @Override
    public int getLoac() {
        return average(IAbResRow::getLoac);
    }

    @Override
    public int getLofc() {
        return average(IAbResRow::getLofc);
    }

    @Override
    public int getNoFl() {
        return average(IAbResRow::getNoFl);
    }

    @Override
    public int getNoFcDup() {
        return average(IAbResRow::getNoFcDup);
    }

    @Override
    public int getNoFcNonDup() {
        return average(IAbResRow::getNoFcNonDup);
    }

    @Override
    public int getNoNest() {
        return average(IAbResRow::getNoNest);
    }

    @Override
    public int getNoNeg() {
        return average(IAbResRow::getNoNeg);
    }

    @Override
    public Collection<AbResRow> getContainedRows() {
        return this.containedRows;
    }
}
