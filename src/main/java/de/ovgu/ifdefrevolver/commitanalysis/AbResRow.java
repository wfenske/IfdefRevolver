package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.output.MethodMetricsColumns;

import java.util.Collection;
import java.util.Collections;

public class AbResRow implements IAbResRow {
    private FunctionId functionId;
    private int loc;
    private int loac = 0;
    private int lofc = 0;
    private int noFl = 0;
    private int noFcDup = 0;
    private int noFcNonDup = 0;
    private int noNest = 0;
    private int noNeg = 0;

    public static AbResRow fromAbResCsvLine(String[] csvLine, FunctionIdFactory functionIdFactory) {
        AbResRow r = new AbResRow();
        String file = parse(csvLine, MethodMetricsColumns.FILE);
        String functionSignature = parse(csvLine, MethodMetricsColumns.FUNCTION_SIGNATURE);

        r.functionId = functionIdFactory.intern(functionSignature, file);

        r.loc = parse(csvLine, MethodMetricsColumns.LOC);
        r.loac = parse(csvLine, MethodMetricsColumns.LOAC);
        r.lofc = parse(csvLine, MethodMetricsColumns.LOFC);
        r.noFl = parse(csvLine, MethodMetricsColumns.NOFL);
        r.noFcDup = parse(csvLine, MethodMetricsColumns.NOFC_Dup);
        r.noFcNonDup = parse(csvLine, MethodMetricsColumns.NOFC_NonDup);
        r.noNest = parse(csvLine, MethodMetricsColumns.NONEST);
        r.noNeg = parse(csvLine, MethodMetricsColumns.NONEG);

        return r;
    }

    public static AbResRow dummyRow(FunctionId id, int loc) {
        AbResRow r = new AbResRow();
        r.functionId = id;
        r.loc = loc;
        return r;
    }

    private static <T> T parse(String[] csvLine, MethodMetricsColumns column) {
        String val = csvLine[column.ordinal()];
        return column.parseCsvColumnValue(val);
    }

    @Override
    public FunctionId getFunctionId() {
        return functionId;
    }

    @Override
    public int getLoc() {
        return loc;
    }

    @Override
    public int getLoac() {
        return loac;
    }

    @Override
    public int getLofc() {
        return lofc;
    }

    @Override
    public int getNoFl() {
        return noFl;
    }

    @Override
    public int getNoFcDup() {
        return noFcDup;
    }

    @Override
    public int getNoFcNonDup() {
        return noFcNonDup;
    }

    @Override
    public int getNoNest() {
        return noNest;
    }

    @Override
    public int getNoNeg() {
        return noNeg;
    }

    @Override
    public Collection<AbResRow> getContainedRows() {
        return Collections.singleton(this);
    }
}
