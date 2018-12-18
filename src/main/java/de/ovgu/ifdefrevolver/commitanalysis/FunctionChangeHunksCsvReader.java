package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.util.SimpleCsvFileReader;
import de.ovgu.skunk.detection.output.CsvEnumUtils;

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
    private CommitsDistanceDb commitsDistanceDb;

    public FunctionChangeHunksCsvReader(CommitsDistanceDb commitsDistanceDb) {
        this.commitsDistanceDb = commitsDistanceDb;
    }

    @Override
    protected boolean hasHeader() {
        return true;
    }

    @Override
    protected void processHeader(String[] headerLine) {
        CsvEnumUtils.validateHeaderRow(FunctionChangeHunksColumns.class, headerLine);
    }

    @Override
    protected void processContentLine(String[] line) {
        FunctionChangeRow result = new FunctionChangeRow();
        String signature = line[FunctionChangeHunksColumns.FUNCTION_SIGNATURE.ordinal()];
        String file = line[FunctionChangeHunksColumns.FILE.ordinal()];
        FunctionId functionId = new FunctionId(signature, file);
        result.functionId = functionId;

        String commitId = line[FunctionChangeHunksColumns.COMMIT_ID.ordinal()];
        Commit commit = commitsDistanceDb.findCommitOrDie(commitId);

        result.previousRevision = parsePreviousRevisionId(line[FunctionChangeHunksColumns.PREVIOUS_REVISION_ID.ordinal()]);

        result.commit = commit;
        String modTypeName = line[FunctionChangeHunksColumns.MOD_TYPE.ordinal()];
        String hunkS = line[FunctionChangeHunksColumns.HUNK.ordinal()];
        result.hunk = Integer.parseInt(hunkS);

        result.linesAdded = parseMandatoryInt(line, FunctionChangeHunksColumns.LINES_ADDED);
        result.linesDeleted = parseMandatoryInt(line, FunctionChangeHunksColumns.LINES_DELETED);

        result.modType = FunctionChangeHunk.ModificationType.valueOf(modTypeName);
        result.newFunctionId = parseNewFunctionId(line);

//        if (commitId.equals("3b15c6f10fe0c205a6a2c263483eb896e13cc79d") && signature.equals("void ldbm_datum_free(LDBM ldbm, Datum data)")) {
//            System.out.println(result);
//        }

        results.add(result);
    }

    private Optional<Commit> parsePreviousRevisionId(String previousRevisionId1) {
        String previousRevisionId = previousRevisionId1;
        final Optional<Commit> previousRevision;
        if ((previousRevisionId == null) || previousRevisionId.isEmpty()) {
            previousRevision = Optional.empty();
        } else {
            previousRevision = Optional.of(commitsDistanceDb.findCommitOrDie(previousRevisionId));
        }
        return previousRevision;
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
