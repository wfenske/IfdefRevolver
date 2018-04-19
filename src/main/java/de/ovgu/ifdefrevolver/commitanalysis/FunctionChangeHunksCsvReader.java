package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.util.SimpleCsvFileReader;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Reads the <code>function_change_hunks.csv</code> file for a snapshot
 */
public class FunctionChangeHunksCsvReader extends SimpleCsvFileReader<List<FunctionChangeRow>> {
    List<FunctionChangeRow> results;

    @Override
    protected boolean hasHeader() {
        return true;
    }

    @Override
    protected void processHeader(String[] headerLine) {
        final int minCols = FunctionChangeHunksColumns.values().length;
        if (headerLine.length < minCols) {
            throw new RuntimeException("Not enough columns. Expected at least " + minCols + ", got " + headerLine.length);
        }

        for (int col = 0; col < minCols; col++) {
            String expectedColName = FunctionChangeHunksColumns.values()[col].name();
            if (!headerLine[col].equalsIgnoreCase(expectedColName)) {
                throw new RuntimeException("Column name mismatch. Expected column " + col + " to be " + expectedColName + ", got: " + headerLine[col]);
            }
        }
    }

    @Override
    protected void processContentLine(String[] line) {
        FunctionChangeRow result = new FunctionChangeRow();
        String signature = line[FunctionChangeHunksColumns.FUNCTION_SIGNATURE.ordinal()];
        String file = line[FunctionChangeHunksColumns.FILE.ordinal()];
        FunctionId functionId = new FunctionId(signature, file);
        result.functionId = functionId;
        result.commitId = line[FunctionChangeHunksColumns.COMMIT_ID.ordinal()];
        String modTypeName = line[FunctionChangeHunksColumns.MOD_TYPE.ordinal()];

        result.linesAdded = parseMandatoryInt(line, FunctionChangeHunksColumns.LINES_ADDED);
        result.linesDeleted = parseMandatoryInt(line, FunctionChangeHunksColumns.LINES_DELETED);

        result.modType = FunctionChangeHunk.ModificationType.valueOf(modTypeName);
        result.newFunctionId = parseNewFunctionId(line);

        results.add(result);
    }

    private int parseMandatoryInt(String[] line, FunctionChangeHunksColumns column) {
        String value = line[column.ordinal()];
        return Integer.parseInt(value);
    }

    private Optional<FunctionId> parseNewFunctionId(String[] line) {
        final Optional<FunctionId> newFunctionId;
        String newSignature = line[FunctionChangeHunksColumns.NEW_FUNCTION_SIGNATURE.ordinal()];
        String newFile = line[FunctionChangeHunksColumns.NEW_FILE.ordinal()];
        boolean noNewSignature = (newSignature == null) || newSignature.isEmpty();
        boolean noNewFile = (newFile == null) || (newFile.isEmpty());
        if (noNewFile != noNewSignature) {
            throw new RuntimeException("New signature and new file must both be set or not at all! Erroneous line: " + line);
        }
        if (noNewFile) {
            newFunctionId = Optional.empty();
        } else {
            newFunctionId = Optional.of(new FunctionId(newSignature, newFile));
        }
        return newFunctionId;
    }

    @Override
    protected void initializeResult() {
        super.initializeResult();
        results = new ArrayList<>();
    }

    @Override
    protected List<FunctionChangeRow> finalizeResult() {
        return results;
    }

    public List<FunctionChangeRow> readFile(IHasResultsDir config, Date snapshotDate) {
        File f = new File(config.snapshotResultsDirForDate(snapshotDate), FunctionChangeHunksColumns.FILE_BASENAME);
        return readFile(f);
    }
}
