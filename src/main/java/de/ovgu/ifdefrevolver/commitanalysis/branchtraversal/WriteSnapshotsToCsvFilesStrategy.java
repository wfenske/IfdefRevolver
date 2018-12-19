package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasSnapshotsDir;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import de.ovgu.skunk.detection.output.CsvRowProvider;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class WriteSnapshotsToCsvFilesStrategy<TConfig extends IHasResultsDir & IHasSnapshotsDir> implements Consumer<List<Snapshot>> {
    private static Logger LOG = Logger.getLogger(WriteSnapshotsToCsvFilesStrategy.class);

    private final CommitsDistanceDb commitsDistanceDb;
    private TConfig config;
    private List<Snapshot> snapshots;

    public WriteSnapshotsToCsvFilesStrategy(CommitsDistanceDb commitsDistanceDb, TConfig config) {
        this.commitsDistanceDb = commitsDistanceDb;
        this.config = config;
    }

    @Override
    public void accept(List<Snapshot> snapshots) {
        this.snapshots = snapshots;

        writeSnapshotFiles();
        writeSnapshotOverviewFile();

        //validateWrittenSnapshots();
    }

    private void writeSnapshotOverviewFile() {
        LOG.debug("Writing snapshot overview CSV for " + snapshots.size() + " snapshot(s).");
        File outputFile = config.snapshotsCsvFile();
        LOG.info("Writing snapshot overview file to " + outputFile);
        CsvRowProvider<Snapshot, Void, SnapshotsColumns> rowProvider = new CsvRowProvider<>(SnapshotsColumns.class, null);

        CsvFileWriterHelper writer = new CsvFileWriterHelper() {
            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                csv.printRecord(rowProvider.headerRow());
                for (Snapshot s : snapshots) {
                    csv.printRecord(rowProvider.dataRow(s));
                }
            }
        };

        try {
            writer.write(outputFile);
        } catch (RuntimeException re) {
            handleExceptionWhileWritingOutputFile(outputFile, re);
        }
    }

    private void writeSnapshotFiles() {
        for (Snapshot s : snapshots) {
            writeSnapshotFile(s);
        }
    }

    private void writeSnapshotFile(Snapshot s) {
        LOG.debug("Writing snapshot CSV for " + s);
        File outputDir = config.snapshotResultsDirForDate(s.getStartDate());
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new RuntimeException("Failed to create output directory " + outputDir);
            }
        }
        File outputFile = new File(outputDir, SnapshotCommitsColumns.FILE_BASENAME);
        LOG.info("Writing snapshot " + s + " to " + outputFile);

        CsvRowProvider<CommitsDistanceDb.Commit, Snapshot, SnapshotCommitsColumns> rowProvider = new CsvRowProvider<>(SnapshotCommitsColumns.class, s);

        CsvFileWriterHelper writer = new CsvFileWriterHelper() {
            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                csv.printRecord(rowProvider.headerRow());
                for (CommitsDistanceDb.Commit commit : s.getCommits()) {
                    csv.printRecord(rowProvider.dataRow(commit));
                }
            }
        };

        try {
            writer.write(outputFile);
        } catch (RuntimeException re) {
            handleExceptionWhileWritingOutputFile(outputFile, re);
            return;
        }
    }

    private void handleExceptionWhileWritingOutputFile(File outputFile, RuntimeException re) {
        LOG.warn("Error writing " + outputFile);
        if (outputFile.exists() && !outputFile.delete()) {
            LOG.warn("Possibly corrupted output file " + outputFile + " could not be deleted.");
        }
        throw re;
    }

    private void validateWrittenSnapshots() {
        final int numExpectedSnapshots = snapshots.size();

        final List<Snapshot> snapshotsRead = SnapshotReader.readSnapshots(commitsDistanceDb, config);
        if (snapshotsRead.size() != numExpectedSnapshots) {
            throw new RuntimeException("Read wrong number of snapshots. Expected: " + numExpectedSnapshots + " got: " + snapshotsRead.size());
        }
        for (int i = 0; i < numExpectedSnapshots; i++) {
            Snapshot expectedSnapshot = snapshots.get(i);
            Snapshot actualSnapshot = snapshotsRead.get(i);
            if (expectedSnapshot.getIndex() != actualSnapshot.getIndex()) {
                throw new RuntimeException("Snapshot indexes don't match. Expected: " + expectedSnapshot + " got: " + actualSnapshot);
            }

            if (!expectedSnapshot.getStartCommit().equals(actualSnapshot.getStartCommit())) {
                throw new RuntimeException("Snapshot start commit hashes don't match. Expected: " + expectedSnapshot + " got: " + actualSnapshot);
            }

            if (!expectedSnapshot.getCommits().equals(actualSnapshot.getCommits())) {
                throw new RuntimeException("Snapshot commit hashes don't match. Expected: " + expectedSnapshot + " got: " + actualSnapshot);
            }

            if (!org.apache.commons.lang3.time.DateUtils.isSameDay(expectedSnapshot.getStartDate(), actualSnapshot.getStartDate())) {
                throw new RuntimeException("Snapshot start dates don't match. Expected: " + expectedSnapshot + " got: " + actualSnapshot);
            }
        }
        LOG.info("Successfully validated all written snapshots.");
    }

}
