package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.util.SimpleCsvFileReader;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Reads the <code>function_change_hunks.csv</code> file for a snapshot
 */
public class AllFunctionsCsvReader extends SimpleCsvFileReader<List<AllFunctionsRow>> {
    List<AllFunctionsRow> results;

    @Override
    protected boolean hasHeader() {
        return true;
    }

    @Override
    protected void processHeader(String[] headerLine) {
        final int minCols = AllSnapshotFunctionsColumns.values().length;
        if (headerLine.length < minCols) {
            throw new RuntimeException("Not enough columns. Expected at least " + minCols + ", got " + headerLine.length);
        }

        for (int col = 0; col < minCols; col++) {
            String expectedColName = AllSnapshotFunctionsColumns.values()[col].name();
            if (!headerLine[col].equalsIgnoreCase(expectedColName)) {
                throw new RuntimeException("Column name mismatch. Expected column " + col + " to be " + expectedColName + ", got: " + headerLine[col]);
            }
        }
    }

    @Override
    protected void processContentLine(String[] line) {
        AllFunctionsRow result = new AllFunctionsRow();
        String signature = line[AllSnapshotFunctionsColumns.FUNCTION_SIGNATURE.ordinal()];
        String file = line[AllSnapshotFunctionsColumns.FILE.ordinal()];
        FunctionId functionId = new FunctionId(signature, file);
        result.functionId = functionId;
        result.loc = Integer.parseUnsignedInt(line[AllSnapshotFunctionsColumns.FUNCTION_LOC.ordinal()]);
        results.add(result);
    }

    @Override
    protected void initializeResult() {
        super.initializeResult();
        results = new ArrayList<>();
    }

    @Override
    protected List<AllFunctionsRow> finalizeResult() {
        return results;
    }

    public List<AllFunctionsRow> readFile(IHasResultsDir config, Date snapshotDate) {
        File f = new File(config.snapshotResultsDirForDate(snapshotDate), AllSnapshotFunctionsColumns.FILE_BASENAME);
        return readFile(f);
    }
}
