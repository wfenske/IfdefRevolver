package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.output.MethodMetricsColumns;

public class AbResRow {
    private FunctionId functionId;
    private int loc;
    private int loac = 0;
    private int lofc = 0;
    private int noFl = 0;
    private int noFcDup = 0;
    private int noFcNonDup = 0;
    private int noNest = 0;
    private int noNeg = 0;

    public static AbResRow fromAbResCsvLine(String[] csvLine) {
        AbResRow r = new AbResRow();
        String file = parse(csvLine, MethodMetricsColumns.FILE);
        String functionSignature = parse(csvLine, MethodMetricsColumns.FUNCTION_SIGNATURE);

        r.functionId = new FunctionId(functionSignature, file);

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

    public FunctionId getFunctionId() {
        return functionId;
    }

    public int getLoc() {
        return loc;
    }

    public int getLoac() {
        return loac;
    }

    public int getLofc() {
        return lofc;
    }

    public int getNoFl() {
        return noFl;
    }

    public int getNoFcDup() {
        return noFcDup;
    }

    public int getNoFcNonDup() {
        return noFcNonDup;
    }

    public int getNoNest() {
        return noNest;
    }

    public int getNoNeg() {
        return noNeg;
    }
}
