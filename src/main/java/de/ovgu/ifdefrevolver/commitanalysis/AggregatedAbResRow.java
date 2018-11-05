package de.ovgu.ifdefrevolver.commitanalysis;

import java.util.ArrayList;
import java.util.Collection;

public class AggregatedAbResRow implements IAbResRow {
    private final FunctionId functionId;
    private final Collection<AbResRow> containedRows;

    public AggregatedAbResRow(IAbResRow left, IAbResRow right) {
        this.containedRows = new ArrayList<>();
        this.containedRows.addAll(left.getContainedRows());
        this.containedRows.addAll(right.getContainedRows());
        this.functionId = left.getContainedRows().iterator().next().getFunctionId();
    }

    @Override
    public FunctionId getFunctionId() {
        return functionId;
    }

    @Override
    public int getLoc() {
        int agg = 0;
        int sz = 0;
        for (AbResRow r : containedRows) {
            agg += r.getLoc();
            sz++;
        }
        return Math.round(agg / ((float) sz));
    }

    @Override
    public int getLoac() {
        int agg = 0;
        int sz = 0;
        for (AbResRow r : containedRows) {
            agg += r.getLoac();
            sz++;
        }
        return Math.round(agg / ((float) sz));
    }

    @Override
    public int getLofc() {
        int agg = 0;
        int sz = 0;
        for (AbResRow r : containedRows) {
            agg += r.getLofc();
            sz++;
        }
        return Math.round(agg / ((float) sz));
    }

    @Override
    public int getNoFl() {
        int agg = 0;
        int sz = 0;
        for (AbResRow r : containedRows) {
            agg += r.getNoFl();
            sz++;
        }
        return Math.round(agg / ((float) sz));
    }

    @Override
    public int getNoFcDup() {
        int agg = 0;
        int sz = 0;
        for (AbResRow r : containedRows) {
            agg += r.getNoFcDup();
            sz++;
        }
        return Math.round(agg / ((float) sz));
    }

    @Override
    public int getNoFcNonDup() {
        int agg = 0;
        int sz = 0;
        for (AbResRow r : containedRows) {
            agg += r.getNoFcNonDup();
            sz++;
        }
        return Math.round(agg / ((float) sz));
    }

    @Override
    public int getNoNest() {
        int agg = 0;
        int sz = 0;
        for (AbResRow r : containedRows) {
            agg += r.getNoNest();
            sz++;
        }
        return Math.round(agg / ((float) sz));
    }

    @Override
    public int getNoNeg() {
        int agg = 0;
        int sz = 0;
        for (AbResRow r : containedRows) {
            agg += r.getNoNeg();
            sz++;
        }
        return Math.round(agg / ((float) sz));
    }

    @Override
    public Collection<AbResRow> getContainedRows() {
        return this.containedRows;
    }
}
