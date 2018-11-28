package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasSnapshotsDir;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.util.SimpleCsvFileReader;
import de.ovgu.skunk.detection.output.CsvEnumUtils;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SnapshotReader {

    private static class SnapshotCommitsCsvReader extends SimpleCsvFileReader<Set<Commit>> {
        private final IHasResultsDir config;
        private LinkedHashSet<Commit> result;
        private final CommitsDistanceDb commitsDistanceDb;

        private SnapshotCommitsCsvReader(CommitsDistanceDb commitsDistanceDb, IHasResultsDir config) {
            this.commitsDistanceDb = commitsDistanceDb;
            this.config = config;
        }

        @Override
        protected void initializeResult() {
            super.initializeResult();
            result = new LinkedHashSet<>();
        }

        @Override
        protected Set<Commit> finalizeResult() {
            return result;
        }

        @Override
        protected boolean hasHeader() {
            return true;
        }

        @Override
        protected void processHeader(String[] headerLine) {
            CsvEnumUtils.validateHeaderRow(SnapshotCommitsColumns.class, headerLine);
        }

        @Override
        protected void processContentLine(String[] line) {
            String commitHash = line[SnapshotCommitsColumns.COMMIT_HASH.ordinal()];
            Commit commit = commitsDistanceDb.internCommit(commitHash);
            result.add(commit);
        }

        public Set<Commit> readFile(Date snapshotDate) {
            File f = new File(config.snapshotResultsDirForDate(snapshotDate), SnapshotCommitsColumns.FILE_BASENAME);
            return readFile(f);
        }
    }

    private static class SnapshotCsvReader<TConfig extends IHasResultsDir & IHasSnapshotsDir> extends SimpleCsvFileReader<List<Snapshot>> {

        private final CommitsDistanceDb commitsDistanceDb;
        private File file;
        private final TConfig config;

        private List<Snapshot> result;

        public SnapshotCsvReader(CommitsDistanceDb commitsDistanceDb, TConfig config) {
            this.commitsDistanceDb = commitsDistanceDb;
            this.config = config;
        }

        @Override
        protected void initializeResult() {
            super.initializeResult();
            result = new ArrayList<>();
        }

        @Override
        protected List<Snapshot> finalizeResult() {
            return result;
        }

        @Override
        protected boolean hasHeader() {
            return true;
        }

        @Override
        protected void processHeader(String[] headerLine) {
            CsvEnumUtils.validateHeaderRow(SnapshotsColumns.class, headerLine);
        }

        private final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

        @Override
        protected void processContentLine(String[] line) {
            Date snapshotDate = parseSnapshotDate(line);
            int snapshotIndex = parseSnapshotIndex(line);
            SnapshotCommitsCsvReader commitsReader = new SnapshotCommitsCsvReader(commitsDistanceDb, config);
            Set<Commit> commits = commitsReader.readFile(snapshotDate);
            Snapshot snapshot = new Snapshot(snapshotIndex, -1, snapshotDate, commits, config.projectSnapshotsDir());
            result.add(snapshot);
        }

        private Date parseSnapshotDate(String[] line) {
            String dateString = line[SnapshotsColumns.SNAPSHOT_DATE.ordinal()];
            try {
                return dateFormatter.parse(dateString);
            } catch (ParseException e) {
                throw new RuntimeException("Error parsing snapshot date " + dateString + " in file " + this.file, e);
            }
        }

        private int parseSnapshotIndex(String[] line) {
            String indexString = line[SnapshotsColumns.SNAPSHOT_INDEX.ordinal()];
            try {
                return Integer.parseInt(indexString);
            } catch (NumberFormatException e) {
                throw new RuntimeException("Error parsing snapshot index " + indexString + " in file " + this.file, e);
            }
        }

        public List<Snapshot> readFile() {
            this.file = new File(config.projectResultsDir(), SnapshotsColumns.FILE_BASENAME);
            return readFile(file);
        }
    }

    public static <TConfig extends IHasResultsDir & IHasSnapshotsDir> List<Snapshot> readSnapshots(CommitsDistanceDb commitsDistanceDb, TConfig config) {
        SnapshotCsvReader<TConfig> reader = new SnapshotCsvReader<>(commitsDistanceDb, config);
        return reader.readFile();
    }
}
