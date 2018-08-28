package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.skunk.detection.output.MethodMetricsColumns;

public class AbResRow {
    private FunctionId functionId;
    private int loc;
    private int loac;
    private int lofc;
    private int noFl;
    private int noFcDup;
    private int noFcNonDup;
    private int noNest;
    private int noNeg;

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
