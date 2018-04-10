package de.ovgu.ifdefrevolver.commitanalysis;

/**
 * A row of the <code>all_functions.csv</code> file
 */
public class AllFunctionsRow {
    public FunctionId functionId;
    public int loc;

    @Override
    public String toString() {
        return "AllFunctionsRow{" +
                "functionId=" + functionId +
                ", loc=" + loc +
                '}';
    }
}
