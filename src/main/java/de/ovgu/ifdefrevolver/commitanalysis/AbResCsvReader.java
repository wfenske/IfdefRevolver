package de.ovgu.ifdefrevolver.commitanalysis;

import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.util.SimpleCsvFileReader;
import de.ovgu.skunk.detection.output.CsvEnumUtils;
import de.ovgu.skunk.detection.output.MethodMetricsColumns;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Reads the <code>ABRes.csv</code> file for a snapshot
 */
public class AbResCsvReader extends SimpleCsvFileReader<List<AbResRow>> {
    List<AbResRow> results;

    @Override
    protected boolean hasHeader() {
        return true;
    }

    @Override
    protected void processHeader(String[] headerLine) {
        CsvEnumUtils.validateHeaderRow(MethodMetricsColumns.class, headerLine);
    }

    @Override
    protected void processContentLine(String[] line) {
        AbResRow result = AbResRow.fromAbResCsvLine(line);
        results.add(result);
    }

    @Override
    protected void initializeResult() {
        super.initializeResult();
        results = new ArrayList<>();
    }

    @Override
    protected List<AbResRow> finalizeResult() {
        return results;
    }

    public List<AbResRow> readFile(IHasResultsDir config, Date snapshotDate) {
        File f = new File(config.snapshotResultsDirForDate(snapshotDate), MethodMetricsColumns.FILE_BASENAME);
        return readFile(f);
    }
}
