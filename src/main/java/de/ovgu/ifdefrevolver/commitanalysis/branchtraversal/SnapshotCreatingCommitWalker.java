package de.ovgu.ifdefrevolver.commitanalysis.branchtraversal;

import de.ovgu.ifdefrevolver.bugs.correlate.data.Snapshot;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasResultsDir;
import de.ovgu.ifdefrevolver.bugs.correlate.main.IHasSnapshotsDir;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb;
import de.ovgu.ifdefrevolver.bugs.minecommits.CommitsDistanceDb.Commit;
import de.ovgu.ifdefrevolver.util.DateUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

public class SnapshotCreatingCommitWalker<TConfig extends IHasResultsDir & IHasSnapshotsDir> extends AbstractCommitWalker {
    private static Logger LOG = Logger.getLogger(SnapshotCreatingCommitWalker.class);

    private final int snapshotSize;
    private List<Snapshot> snapshots;
    private List<Commit> commitsInCurrentSnapshot;
    private final TConfig config;
    private int numberOfRelevantCommitsInCurrentSnapshot;
    private final Set<Commit> commitsThatModifyCFiles;
    private final Consumer<List<Snapshot>> outputStrategy;

    public SnapshotCreatingCommitWalker(CommitsDistanceDb commitsDistanceDb, TConfig config, int snapshotSize, Set<Commit> commitsThatModifyCFiles, Consumer<List<Snapshot>> outputStrategy) {
        super(commitsDistanceDb);
        this.config = config;
        this.snapshotSize = snapshotSize;
        this.commitsThatModifyCFiles = commitsThatModifyCFiles;
        this.outputStrategy = outputStrategy;
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

    @Override
    protected void onAllCommitsProcessed() {
        super.onAllCommitsProcessed();

        outputStrategy.accept(snapshots);

        final int numSnapshots = snapshots.size();
        final int commitsInSnapshots = snapshots.stream().mapToInt(s -> s.getCommits().size()).sum();
        final int discardedCommits = commitsInCurrentSnapshot.size();
        final long discardedCommitsThatModifyCFiles = commitsInCurrentSnapshot.stream().filter(this::isCommitRelevant).count();

        LOG.info("Collected " + commitsInSnapshots + " commit(s) in " + numSnapshots + " snapshot(s). " +
                "Discarded " + discardedCommits + " commit(s) (" +
                discardedCommitsThatModifyCFiles + " of which modify .c files) " +
                "since it/they did not fill up a whole snapshot.");
    }

    @Override
    protected void processCurrentCommit() {
        this.commitsInCurrentSnapshot.add(this.currentCommit);
        if (isCommitRelevant(this.currentCommit)) {
            this.numberOfRelevantCommitsInCurrentSnapshot++;
        }

        if (this.numberOfRelevantCommitsInCurrentSnapshot == snapshotSize) {
            createSnapshotFromCurrentCommits();
            startNewSnapshot();
        }
    }

    private boolean isCommitRelevant(Commit c) {
        return this.commitsThatModifyCFiles.contains(c);
    }

    private void startNewSnapshot() {
        this.commitsInCurrentSnapshot = new ArrayList<>();
        this.numberOfRelevantCommitsInCurrentSnapshot = 0;
    }

    private void createSnapshotFromCurrentCommits() {
        final Commit startCommit = this.commitsInCurrentSnapshot.get(0);
        Calendar snapshotCal = DateUtils.toCalendar(startCommit.getTimestamp());
        snapshotCal = ensureSnapshotDatesAreUnique(snapshotCal);
        Date snapshotDate = snapshotCal.getTime();
        File snapshotDir = config.snapshotDirForDate(snapshotDate);
        Snapshot s = new Snapshot(this.snapshots.size(), snapshotDate,
                new LinkedHashSet<>(this.commitsInCurrentSnapshot), snapshotDir);
        this.snapshots.add(s);
    }

    private Calendar ensureSnapshotDatesAreUnique(Calendar snapshotCal) {
        if (snapshots.isEmpty()) return snapshotCal;
        String originalStartDate = "";
        if (LOG.isInfoEnabled()) {
            originalStartDate = formatSnapshotDate(snapshotCal);
        }

        final Snapshot previousSnapshot = snapshots.get(snapshots.size() - 1);
        Date previousSnapshotStartDate = previousSnapshot.getStartDate();
        Calendar lastSnapshotCal = DateUtils.toCalendar(previousSnapshotStartDate);
        int advances = 0;
        while (!DateUtils.isAtLeastOneDayBefore(lastSnapshotCal, snapshotCal)) {
            snapshotCal.add(Calendar.DAY_OF_YEAR, 1);
            advances++;
        }

        if (LOG.isInfoEnabled() && (advances > 0)) {
            LOG.info("Advanced snapshot date by " + advances + " day(s) to ensure it is unique. Old date: " +
                    originalStartDate + " New date: " + formatSnapshotDate(snapshotCal));
        }

        return snapshotCal;
    }

    private static String formatSnapshotDate(Calendar cal) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        return df.format(cal.getTime());
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
