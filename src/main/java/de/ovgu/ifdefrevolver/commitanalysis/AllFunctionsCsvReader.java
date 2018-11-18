package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.util.SimpleCsvFileReader;
import de.ovgu.skunk.detection.output.CsvEnumUtils;

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
        CsvEnumUtils.validateHeaderRow(AllSnapshotFunctionsColumns.class, headerLine);
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

    public List<AllFunctionsRow> readFile(IHasResultsDir config, String commitHash) {
        File f = getFileForCommitHash(config, commitHash);
        return readFile(f);
    }

    public static boolean fileExists(IHasResultsDir config, String commitHash) {
        File f = getFileForCommitHash(config, commitHash);
        return f.exists() && !f.isDirectory();
    }

    public static File getFileForCommitHash(IHasResultsDir config, String commitHash) {
        return new File(config.snapshotResultsDirForCommit(commitHash), AllSnapshotFunctionsColumns.FILE_BASENAME);
    }
}
