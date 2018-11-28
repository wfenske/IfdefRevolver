package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasSnapshotsDir;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.commitanalysis.GitUtil;
import de.ovgu.ifdefrevolver.commitanalysis.IHasRepoDir;
import de.ovgu.ifdefrevolver.util.DateUtils;
import de.ovgu.skunk.detection.output.CsvFileWriterHelper;
import de.ovgu.skunk.detection.output.CsvRowProvider;
import org.apache.commons.csv.CSVPrinter;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SnapshotCreatingCommitWalker<TConfig extends IHasResultsDir & IHasSnapshotsDir & IHasRepoDir> extends AbstractCommitWalker {
    private static Logger LOG = Logger.getLogger(SnapshotCreatingCommitWalker.class);

    private final int snapshotSize;
    private List<Snapshot> snapshots;
    private List<Commit> commitsInCurrentSnapshot;
    private Calendar lastSnapshotCal = null;
    private final TConfig config;

    public SnapshotCreatingCommitWalker(CommitsDistanceDb commitsDistanceDb, TConfig config, int snapshotSize) {
        super(commitsDistanceDb);
        this.config = config;
        this.snapshotSize = snapshotSize;
    }

    @Override
    public void processCommits() {
        this.snapshots = new ArrayList<>();

        if (snapshotSize <= 0) {
            throw new IllegalArgumentException("Invalid snapshot size (should be >= 1): " + snapshotSize);
        }

        startNewSnapshot();
        super.processCommits();
    }

    private void writeSnapshotOverviewFile() {
        LOG.debug("Writing snapshot overview CSV for " + snapshots.size() + " snapshot(s).");
        File outputDir = config.projectResultsDir();
        File outputFile = new File(outputDir, SnapshotsColumns.FILE_BASENAME);
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
        File outputDir = config.snapshotResultsDirForDate(s.getSnapshotDate());
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new RuntimeException("Failed to create output directory " + outputDir);
            }
        }
        File outputFile = new File(outputDir, SnapshotCommitsColumns.FILE_BASENAME);
        LOG.info("Writing snapshot " + s + " to " + outputFile);

        CsvRowProvider<Commit, Snapshot, SnapshotCommitsColumns> rowProvider = new CsvRowProvider<>(SnapshotCommitsColumns.class, s);

        CsvFileWriterHelper writer = new CsvFileWriterHelper() {
            @Override
            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
                csv.printRecord(rowProvider.headerRow());
                for (Commit commit : s.getCommits()) {
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

    @Override
    protected void onAllCommitsProcessed() {
        super.onAllCommitsProcessed();

        writeSnapshotFiles();
        writeSnapshotOverviewFile();

        final int numSnapshots = snapshots.size();
        final int commitsInSnapshots = numSnapshots * snapshotSize;
        final int discardedCommits = commitsInCurrentSnapshot.size();
        LOG.info("Collected " + commitsInSnapshots + " commit(s) in " + numSnapshots + " snapshot(s). " +
                "Discarded " + discardedCommits + " commit(s) since it/they did not fill up a whole snapshot.");

        //validateWrittenSnapshots();
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
            if (expectedSnapshot.getSnapshotIndex() != actualSnapshot.getSnapshotIndex()) {
                throw new RuntimeException("Snapshot indexes don't match. Expected: " + expectedSnapshot + " got: " + actualSnapshot);
            }

            if (!expectedSnapshot.getStartCommit().equals(actualSnapshot.getStartCommit())) {
                throw new RuntimeException("Snapshot start commit hashes don't match. Expected: " + expectedSnapshot + " got: " + actualSnapshot);
            }

            if (!expectedSnapshot.getCommits().equals(actualSnapshot.getCommits())) {
                throw new RuntimeException("Snapshot commit hashes don't match. Expected: " + expectedSnapshot + " got: " + actualSnapshot);
            }

            if (!org.apache.commons.lang3.time.DateUtils.isSameDay(expectedSnapshot.getSnapshotDate(), actualSnapshot.getSnapshotDate())) {
                throw new RuntimeException("Snapshot start dates don't match. Expected: " + expectedSnapshot + " got: " + actualSnapshot);
            }
        }
        LOG.info("Successfully validated all written snapshots.");
    }

    @Override
    protected void processCurrentCommit() {
        this.commitsInCurrentSnapshot.add(this.currentCommit);
        if ((this.commitsInCurrentSnapshot.size() % snapshotSize) == 0) {
            createSnapshotFromCurrentCommits();
            startNewSnapshot();
        }
    }

    private void startNewSnapshot() {
        this.commitsInCurrentSnapshot = new ArrayList<>();
    }

    private void createSnapshotFromCurrentCommits() {
        final int branch = -1;
        final Commit startCommit = this.commitsInCurrentSnapshot.get(0);
        Calendar snapshotDateCal = GitUtil.getAuthorDateOfCommit(config.getRepoDir(), startCommit.commitHash);
        snapshotDateCal = ensureSnapshotDatesAreUnique(snapshotDateCal);
        Date snapshotDate = snapshotDateCal.getTime();
        Snapshot s = new Snapshot(this.snapshots.size(), branch, snapshotDate,
                new LinkedHashSet<>(this.commitsInCurrentSnapshot), config.snapshotDirForDate(snapshotDate));
        this.snapshots.add(s);
    }

    private Calendar ensureSnapshotDatesAreUnique(Calendar snapshotDateCal) {
        if (lastSnapshotCal == null) return snapshotDateCal;
        int advances = 0;
        while (!DateUtils.isAtLeastOneDayBefore(lastSnapshotCal, snapshotDateCal)) {
            snapshotDateCal.add(Calendar.DAY_OF_YEAR, 1);
            advances++;
        }
        if (advances > 0) {
            LOG.info("Advanced snapshot date by " + advances + " day(s) to ensure it is unique. New snapshot date is " + snapshotDateCal);
        }

        return snapshotDateCal;
    }

//    private void writeCurrentSnapshotToFile() {
//        CsvRowProvider<String, CommitWalkerContext, CommitWalkerSnapshotColumns> rowProvider;
//        CsvFileWriterHelper writer = new CsvFileWriterHelper() {
//            @Override
//            protected void actuallyDoStuff(CSVPrinter csv) throws IOException {
//
//            }
//        };
//        File outputFile = new File(config)
//
//        writer.write(outputFile);
//    }
}
